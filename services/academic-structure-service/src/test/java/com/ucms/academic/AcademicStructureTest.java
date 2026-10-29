package com.ucms.academic;

import com.ucms.academic.domain.*;
import com.ucms.academic.repository.*;
import com.ucms.academic.security.AssignmentCache;
import com.ucms.academic.security.AssignmentClaim;
import com.ucms.academic.security.AssignmentsUnavailableException;
import com.ucms.academic.security.RoleCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Matches html-wireframes/index.html's Academic Calendar / Degree Programs / Course
 * Catalog / Courses screens. Seeds its own fixtures rather than relying on Flyway, which
 * is disabled for the H2 test profile — same pattern as base-service's tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AcademicStructureTest {

    @Autowired MockMvc mockMvc;
    @Autowired AcademicYearRepository academicYearRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired DegreeProgramRepository degreeProgramRepository;
    @Autowired CourseCatalogRepository courseCatalogRepository;
    @Autowired ProgramCourseRepository programCourseRepository;
    @Autowired CourseRepository courseRepository;
    @MockBean RoleCatalog roleCatalogClient;
    @MockBean AssignmentCache assignmentCache;

    private AcademicYear activeYear;
    private AcademicYear archivedYear;
    private Semester activeSemester;
    private DegreeProgram program;
    private CourseCatalog catalogCourse;
    private UUID lecturerId;

    /** The seeded Administrator role — what a trusted service token resolves to here. */
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LECTURER_ROLE_ID = UUID.randomUUID();
    private static final UUID STUDENT_ROLE_ID = UUID.randomUUID();
    private static final UUID DEPT_HEAD_ROLE_ID = UUID.randomUUID();

    @BeforeEach
    void seed() {
        // Mirrors the real role_permissions baseline seeded by V5__rbac_scope_assignments.sql
        // for ADMIN/LECTURER/STUDENT, plus a Department-Head-shaped role for scope tests —
        // the real RoleCatalogClient (an HTTP sync to identity-access-service) is mocked
        // out here so these are deterministic, hermetic unit tests. See
        // docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3.
        when(roleCatalogClient.baselineGrant(eq(ADMIN_ROLE_ID), anyString(), anyString())).thenReturn(true);
        when(roleCatalogClient.baselineGrant(eq(LECTURER_ROLE_ID), eq("Degree Programs"), eq("View"))).thenReturn(true);
        when(roleCatalogClient.baselineGrant(eq(LECTURER_ROLE_ID), eq("Courses"), anyString())).thenReturn(true);
        when(roleCatalogClient.baselineGrant(eq(STUDENT_ROLE_ID), anyString(), eq("View"))).thenReturn(true);
        when(roleCatalogClient.baselineGrant(eq(DEPT_HEAD_ROLE_ID), eq("Degree Programs"), anyString())).thenReturn(true);
        when(roleCatalogClient.baselineGrant(eq(DEPT_HEAD_ROLE_ID), eq("Courses"), anyString())).thenReturn(true);
        activeYear = new AcademicYear();
        activeYear.setName("2025/2026");
        activeYear.setStartDate(LocalDate.of(2025, 9, 1));
        activeYear.setEndDate(LocalDate.of(2026, 6, 30));
        activeYear.setStatus(AcademicYearStatus.ACTIVE);
        activeYear = academicYearRepository.save(activeYear);

        activeSemester = new Semester();
        activeSemester.setAcademicYear(activeYear);
        activeSemester.setName("Semester 1");
        activeSemester.setSequence(1);
        activeSemester.setStatus(SemesterStatus.ACTIVE);
        activeSemester = semesterRepository.save(activeSemester);

        program = new DegreeProgram();
        program.setCode("BSc-CS");
        program.setName("BSc in Computer Science");
        program.setDepartmentId(UUID.randomUUID());
        program.setDurationYears(4);
        program.setTotalCredits(120);
        program.setStatus(ProgramStatus.ACTIVE);
        program = degreeProgramRepository.save(program);

        catalogCourse = new CourseCatalog();
        catalogCourse.setCode("COSC5105");
        catalogCourse.setName("Introduction to Programming");
        catalogCourse.setDefaultCredits(3);
        catalogCourse.setOwningDepartmentId(UUID.randomUUID());
        catalogCourse = courseCatalogRepository.save(catalogCourse);

        archivedYear = new AcademicYear();
        archivedYear.setName("2024/2025");
        archivedYear.setStartDate(LocalDate.of(2024, 9, 1));
        archivedYear.setEndDate(LocalDate.of(2025, 6, 30));
        archivedYear.setStatus(AcademicYearStatus.ARCHIVED);
        archivedYear = academicYearRepository.save(archivedYear);

        lecturerId = UUID.randomUUID();
    }

    private String tokenFor(String role) {
        return tokenFor(role, UUID.randomUUID());
    }

    /** Embeds a GLOBAL assignment matching the role, mirroring what a real login
     * issues for every account (see AuthService/UserService) — makes these hand-crafted
     * test tokens behave like real ones under the new PermissionResolver. */
    private String tokenFor(String role, UUID subject) {
        UUID roleId = switch (role) {
            case "ADMIN" -> ADMIN_ROLE_ID;
            case "LECTURER" -> LECTURER_ROLE_ID;
            default -> STUDENT_ROLE_ID;
        };
        return tokenWithAssignment(role, subject, roleId, "GLOBAL", null);
    }

    private String tokenWithDepartmentAssignment(String role, UUID subject, UUID roleId, UUID departmentId) {
        return tokenWithAssignment(role, subject, roleId, "DEPARTMENT", departmentId);
    }

    private String tokenWithAssignment(String role, UUID subject, UUID roleId, String scopeType, UUID scopeId) {
        return tokenWithAssignment(role, subject, roleId, scopeType, scopeId, Map.of());
    }

    /** G-09: like tokenWithAssignment, but lets a test embed a per-assignment
     * permission override (keyed by permission id, same shape identity-access-service's
     * PATCH /assignments/{id}/overrides would persist) so PermissionResolver's override
     * handling can be exercised end-to-end. */
    private String tokenWithAssignment(String role, UUID subject, UUID roleId, String scopeType, UUID scopeId,
                                        Map<UUID, Boolean> overrides) {
        Map<String, Object> rawOverrides = new java.util.HashMap<>();
        overrides.forEach((permissionId, granted) -> rawOverrides.put(permissionId.toString(), granted));
        Map<String, Object> assignment = Map.of(
                "id", UUID.randomUUID().toString(),
                "roleId", roleId.toString(),
                "scopeType", scopeType,
                "scopeId", scopeId == null ? "" : scopeId.toString(),
                "overrides", rawOverrides);
        return KeycloakTestTokens.sign(Map.of(
                        "azp", "ucms-web",
                        "uid", subject.toString(),
                        "realm_access", Map.of("roles", List.of(role)),
                        "assignments", List.of(assignment)),
                Instant.now().plus(60, ChronoUnit.MINUTES));
    }

    // ---- Academic Calendar ----

    @Test
    void listAcademicYears_isReachableByAnyAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/academic-years").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listAcademicYears_withNoToken_isRejected() throws Exception {
        mockMvc.perform(get("/academic-years")).andExpect(status().isForbidden());
    }

    @Test
    void createAcademicYear_asAdmin_withDefaultSemesters_succeeds() throws Exception {
        mockMvc.perform(post("/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2026/2027\",\"startDate\":\"2026-09-01\",\"endDate\":\"2027-06-30\"," +
                                "\"status\":\"UPCOMING\",\"createDefaultSemesters\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("2026/2027"));

        mockMvc.perform(get("/academic-years").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void createAcademicYear_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(post("/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("LECTURER"))
                        .contentType("application/json")
                        .content("{\"name\":\"2026/2027\",\"status\":\"UPCOMING\",\"createDefaultSemesters\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAcademicYear_withDuplicateName_isRejected() throws Exception {
        mockMvc.perform(post("/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2025/2026\",\"status\":\"UPCOMING\",\"createDefaultSemesters\":false}"))
                .andExpect(status().isConflict());
    }

    // ---- Multiple academic years can be ACTIVE at once (3-4 year degree programs mean
    // several years of study are concurrently live); only ARCHIVED years are read-only ----

    @Test
    void createAcademicYear_asActive_doesNotArchiveOtherActiveYears() throws Exception {
        mockMvc.perform(post("/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2026/2027\",\"status\":\"ACTIVE\",\"createDefaultSemesters\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String body = mockMvc.perform(get("/academic-years").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andReturn().getResponse().getContentAsString();
        var years = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        for (var node : years) {
            if (node.get("name").asText().equals("2025/2026")) {
                org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", node.get("status").asText());
            }
            if (node.get("name").asText().equals("2026/2027")) {
                org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", node.get("status").asText());
            }
        }
    }

    @Test
    void activate_doesNotDeactivateOtherActiveYears() throws Exception {
        mockMvc.perform(post("/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2026/2027\",\"status\":\"UPCOMING\",\"createDefaultSemesters\":false}"))
                .andExpect(status().isCreated());

        String yearsJson = mockMvc.perform(get("/academic-years").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode years = new com.fasterxml.jackson.databind.ObjectMapper().readTree(yearsJson);
        String newYearId = null;
        for (var node : years) {
            if (node.get("name").asText().equals("2026/2027")) newYearId = node.get("id").asText();
        }

        mockMvc.perform(patch("/academic-years/" + newYearId + "/activate")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String afterBody = mockMvc.perform(get("/academic-years").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andReturn().getResponse().getContentAsString();
        var afterYears = new com.fasterxml.jackson.databind.ObjectMapper().readTree(afterBody);
        for (var node : afterYears) {
            if (node.get("name").asText().equals("2025/2026")) {
                org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", node.get("status").asText());
            }
            if (node.get("name").asText().equals("2026/2027")) {
                org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", node.get("status").asText());
            }
        }
    }

    @Test
    void addSemester_toArchivedYear_isRejected() throws Exception {
        archivedYear = academicYearRepository.findByIdAndDeletedAtIsNull(archivedYear.getId()).orElseThrow();
        mockMvc.perform(post("/academic-years/" + archivedYear.getId() + "/semesters")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Extra Semester\",\"sequence\":9,\"status\":\"UPCOMING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void assignCourseToArchivedYear_isRejected() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        String body = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":10,\"status\":\"DRAFT\"}"))
                .andReturn().getResponse().getContentAsString();
        String courseId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(post("/courses/" + courseId + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + archivedYear.getId() + "\",\"semesterId\":\"" + activeSemester.getId() + "\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listCourses_filteredByDegreeProgramId_narrowsResults() throws Exception {
        DegreeProgram otherProgram = new DegreeProgram();
        otherProgram.setCode("BSc-OTHER");
        otherProgram.setName("Other Program");
        otherProgram.setDepartmentId(UUID.randomUUID());
        otherProgram.setDurationYears(3);
        otherProgram.setTotalCredits(90);
        otherProgram.setStatus(ProgramStatus.ACTIVE);
        otherProgram = degreeProgramRepository.save(otherProgram);

        Course inOtherProgram = new Course();
        inOtherProgram.setCatalogCourse(catalogCourse);
        inOtherProgram.setDegreeProgram(otherProgram);
        inOtherProgram.setBeginDate(activeYear.getStartDate());
        inOtherProgram.setYearOfStudy(1);
        inOtherProgram.setLecturerId(lecturerId);
        inOtherProgram.setCapacity(20);
        inOtherProgram.setStatus(CourseStatus.PUBLISHED);
        courseRepository.save(inOtherProgram);

        mockMvc.perform(get("/courses").param("degreeProgramId", otherProgram.getId().toString())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].degreeProgramId").value(otherProgram.getId().toString()));
    }

    @Test
    void addSemester_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/academic-years/" + activeYear.getId() + "/semesters")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Semester 2\",\"sequence\":2,\"status\":\"UPCOMING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Semester 2"));
    }

    @Test
    void addSemester_withDuplicateNameInYear_isRejected() throws Exception {
        mockMvc.perform(post("/academic-years/" + activeYear.getId() + "/semesters")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Semester 1\",\"sequence\":1,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addSemester_forMissingYear_returnsNotFound() throws Exception {
        mockMvc.perform(post("/academic-years/" + UUID.randomUUID() + "/semesters")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Semester 2\",\"sequence\":2,\"status\":\"UPCOMING\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- Degree Programs & Curriculum ----

    @Test
    void listDegreePrograms_isReachableByAnyAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/degree-programs").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("BSc-CS"));
    }

    // ---- Phase 3: Department Head scoping (docs/SYSTEM_ARCHITECTURE_REPORT.md section 5/7.2, TC-P3-*) ----

    @Test
    void listDegreePrograms_asDeptHead_showsOnlyOwnDepartment() throws Exception {
        DegreeProgram otherProgram = new DegreeProgram();
        otherProgram.setCode("BSc-SE");
        otherProgram.setName("BSc in Software Engineering");
        otherProgram.setDepartmentId(UUID.randomUUID());
        otherProgram.setDurationYears(4);
        otherProgram.setTotalCredits(120);
        otherProgram.setStatus(ProgramStatus.ACTIVE);
        degreeProgramRepository.save(otherProgram);

        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(get("/degree-programs").header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("BSc-CS"));
    }

    @Test
    void listDegreePrograms_withNoMatchingDepartment_showsNone() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(get("/degree-programs").header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Keycloak tokens carry no assignments: they're loaded from AssignmentCache ----

    @Test
    void assignmentsMissingFromTheToken_areLoadedFromTheCache() throws Exception {
        UUID deptHead = UUID.randomUUID();
        when(assignmentCache.forUser(deptHead)).thenReturn(List.of(new AssignmentClaim(
                UUID.randomUUID(), DEPT_HEAD_ROLE_ID, "DEPARTMENT", program.getDepartmentId(), Map.of())));

        mockMvc.perform(get("/degree-programs")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(deptHead, "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("BSc-CS"));
    }

    @Test
    void assignmentsUnavailable_isA503_notASignOut() throws Exception {
        UUID user = UUID.randomUUID();
        when(assignmentCache.forUser(user)).thenThrow(new AssignmentsUnavailableException(new RuntimeException("down")));

        mockMvc.perform(get("/degree-programs")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(user, "LECTURER")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void trustedService_seesEveryDepartment_likeTheOldServiceToken() throws Exception {
        // base-service's department-dependents check (G-05) relies on this.
        mockMvc.perform(get("/degree-programs").param("departmentId", program.getDepartmentId().toString())
                        .header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:academic")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void serviceNotGrantedThisService_isRejected() throws Exception {
        mockMvc.perform(get("/degree-programs")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:identity")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createDegreeProgram_asDeptHeadForOwnDepartment_succeeds() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(post("/degree-programs")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"code\":\"Dip-IT\",\"name\":\"Diploma in IT\"," +
                                "\"departmentId\":\"" + program.getDepartmentId() + "\",\"durationYears\":2,\"totalCredits\":60,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createDegreeProgram_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(post("/degree-programs")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"code\":\"Dip-IT\",\"name\":\"Diploma in IT\"," +
                                "\"departmentId\":\"" + UUID.randomUUID() + "\",\"durationYears\":2,\"totalCredits\":60,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addCurriculumEntry_asDeptHeadForOwnDepartment_succeeds() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"requirementType\":\"CORE\"," +
                                "\"recommendedYear\":1,\"recommendedSemester\":1,\"creditValue\":3}"))
                .andExpect(status().isCreated());
    }

    @Test
    void addCurriculumEntry_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"requirementType\":\"CORE\"," +
                                "\"recommendedYear\":1,\"recommendedSemester\":1,\"creditValue\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCourses_mineTrue_asDeptHead_seesAllDepartmentCoursesNotJustOwn() throws Exception {
        Course course = new Course();
        course.setCatalogCourse(catalogCourse);
        course.setDegreeProgram(program);
        course.setBeginDate(activeYear.getStartDate());
        course.setYearOfStudy(1);
        course.setLecturerId(UUID.randomUUID()); // a different lecturer entirely
        course.setCapacity(30);
        course.setStatus(CourseStatus.PUBLISHED);
        courseRepository.save(course);

        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(get("/courses").param("mine", "true").header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createDegreeProgram_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/degree-programs")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"code\":\"BSc-SE\",\"name\":\"BSc in Software Engineering\"," +
                                "\"departmentId\":\"" + UUID.randomUUID() + "\",\"durationYears\":4,\"totalCredits\":120,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BSc-SE"));
    }

    @Test
    void createDegreeProgram_withDuplicateCode_isRejected() throws Exception {
        mockMvc.perform(post("/degree-programs")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"code\":\"BSc-CS\",\"name\":\"Duplicate\"," +
                                "\"departmentId\":\"" + UUID.randomUUID() + "\",\"durationYears\":4,\"totalCredits\":120,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createDegreeProgram_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(post("/degree-programs")
                        .header("Authorization", "Bearer " + tokenFor("LECTURER"))
                        .contentType("application/json")
                        .content("{\"code\":\"BSc-SE\",\"name\":\"BSc in Software Engineering\"," +
                                "\"departmentId\":\"" + UUID.randomUUID() + "\",\"durationYears\":4,\"totalCredits\":120,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addCurriculumEntry_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"requirementType\":\"CORE\"," +
                                "\"recommendedYear\":1,\"recommendedSemester\":1,\"creditValue\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogCourseCode").value("COSC5105"));

        mockMvc.perform(get("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void addCurriculumEntry_duplicateCourseInProgram_isRejected() throws Exception {
        ProgramCourse pc = new ProgramCourse();
        pc.setDegreeProgram(program);
        pc.setCatalogCourse(catalogCourse);
        pc.setRequirementType(RequirementType.CORE);
        pc.setRecommendedYear(1);
        pc.setRecommendedSemester(1);
        pc.setCreditValue(3);
        programCourseRepository.save(pc);

        mockMvc.perform(post("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"requirementType\":\"ELECTIVE\"," +
                                "\"recommendedYear\":2,\"recommendedSemester\":1,\"creditValue\":3}"))
                .andExpect(status().isConflict());
    }

    // ---- G-07: listCurriculum was previously unguarded ----

    @Test
    void listCurriculum_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(get("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCurriculum_asDeptHeadForOwnDepartment_succeeds() throws Exception {
        ProgramCourse pc = new ProgramCourse();
        pc.setDegreeProgram(program);
        pc.setCatalogCourse(catalogCourse);
        pc.setRequirementType(RequirementType.CORE);
        pc.setRecommendedYear(1);
        pc.setRecommendedSemester(1);
        pc.setCreditValue(3);
        programCourseRepository.save(pc);

        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(get("/degree-programs/" + program.getId() + "/curriculum")
                        .header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- Degree Program <-> Academic Year assignment ----

    @Test
    void listDegreePrograms_withDepartmentFilter_narrowsResults() throws Exception {
        DegreeProgram otherDeptProgram = new DegreeProgram();
        otherDeptProgram.setCode("BSc-OTHER");
        otherDeptProgram.setName("Other");
        otherDeptProgram.setDepartmentId(UUID.randomUUID());
        otherDeptProgram.setDurationYears(3);
        otherDeptProgram.setTotalCredits(90);
        otherDeptProgram.setStatus(ProgramStatus.ACTIVE);
        degreeProgramRepository.save(otherDeptProgram);

        mockMvc.perform(get("/degree-programs").param("departmentId", program.getDepartmentId().toString())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("BSc-CS"));
    }

    @Test
    void assignDegreeProgramToAcademicYear_succeeds() throws Exception {
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + archivedYear.getId() + "\",\"status\":\"DISCONTINUED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));

        mockMvc.perform(get("/degree-program-academic-years").param("academicYearId", archivedYear.getId().toString())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DISCONTINUED"));
    }

    @Test
    void assignDegreeProgramToAcademicYear_duplicateYear_isRejected() throws Exception {
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void assignDegreeProgramToAcademicYear_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- Course Catalog ----

    @Test
    void listCourseCatalog_returnsSeededSubject() throws Exception {
        mockMvc.perform(get("/course-catalog").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("COSC5105"));
    }

    @Test
    void createCourseCatalogEntry_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/course-catalog")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"code\":\"MATH2031\",\"name\":\"Discrete Mathematics\",\"defaultCredits\":3," +
                                "\"owningDepartmentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MATH2031"));
    }

    @Test
    void createCourseCatalogEntry_withDuplicateCode_isRejected() throws Exception {
        mockMvc.perform(post("/course-catalog")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"code\":\"COSC5105\",\"name\":\"Duplicate\",\"defaultCredits\":3," +
                                "\"owningDepartmentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    // ---- G-08: Course Catalog PUT/DELETE + departmentId filter ----

    @Test
    void listCourseCatalog_withDepartmentFilter_narrowsResults() throws Exception {
        CourseCatalog otherDeptSubject = new CourseCatalog();
        otherDeptSubject.setCode("PHYS1010");
        otherDeptSubject.setName("Physics I");
        otherDeptSubject.setDefaultCredits(3);
        otherDeptSubject.setOwningDepartmentId(UUID.randomUUID());
        courseCatalogRepository.save(otherDeptSubject);

        mockMvc.perform(get("/course-catalog").param("departmentId", catalogCourse.getOwningDepartmentId().toString())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("COSC5105"));
    }

    @Test
    void updateCourseCatalogEntry_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/course-catalog/" + catalogCourse.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Introduction to Programming (Revised)\",\"defaultCredits\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Introduction to Programming (Revised)"))
                .andExpect(jsonPath("$.defaultCredits").value(4))
                .andExpect(jsonPath("$.code").value("COSC5105"));
    }

    @Test
    void deleteCourseCatalogEntry_blockedByDependentCourse_isRejected() throws Exception {
        newCourse(CourseStatus.PUBLISHED);
        mockMvc.perform(delete("/course-catalog/" + catalogCourse.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCourseCatalogEntry_blockedByDependentProgramCourse_isRejected() throws Exception {
        ProgramCourse pc = new ProgramCourse();
        pc.setDegreeProgram(program);
        pc.setCatalogCourse(catalogCourse);
        pc.setRequirementType(RequirementType.CORE);
        pc.setRecommendedYear(1);
        pc.setRecommendedSemester(1);
        pc.setCreditValue(3);
        programCourseRepository.save(pc);

        mockMvc.perform(delete("/course-catalog/" + catalogCourse.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCourseCatalogEntry_withNoDependents_succeeds() throws Exception {
        mockMvc.perform(delete("/course-catalog/" + catalogCourse.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/course-catalog").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Course Offerings ----

    @Test
    void createCourse_asLecturer_setsCallerAsLecturer() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogCourseCode").value("COSC5105"))
                .andExpect(jsonPath("$.degreeProgramId").value(program.getId().toString()))
                .andExpect(jsonPath("$.capacity").value(60));

        mockMvc.perform(get("/courses?mine=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createCourse_asAdminOnBehalfOfLecturer_succeeds() throws Exception {
        UUID targetLecturer = UUID.randomUUID();
        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"," +
                                "\"lecturerId\":\"" + targetLecturer + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecturerId").value(targetLecturer.toString()));
    }

    @Test
    void createCourse_plainLecturerOnBehalfOfAnother_isRejected() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + tokenFor("LECTURER", lecturerId))
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"," +
                                "\"lecturerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCourse_deptHeadOnBehalfOfLecturerInOwnDepartment_succeeds() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"," +
                                "\"lecturerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void listCourses_mineFalse_returnsAllForAdminOversight() throws Exception {
        Course course = new Course();
        course.setCatalogCourse(catalogCourse);
        course.setDegreeProgram(program);
        course.setBeginDate(activeYear.getStartDate());
        course.setYearOfStudy(1);
        course.setLecturerId(lecturerId);
        course.setCapacity(60);
        course.setStatus(CourseStatus.PUBLISHED);
        courseRepository.save(course);

        mockMvc.perform(get("/courses").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].degreeProgramCode").value("BSc-CS"));
    }

    @Test
    void listCourses_mineTrue_scopesToDifferentLecturer_returnsEmpty() throws Exception {
        Course course = new Course();
        course.setCatalogCourse(catalogCourse);
        course.setDegreeProgram(program);
        course.setBeginDate(activeYear.getStartDate());
        course.setYearOfStudy(1);
        course.setLecturerId(lecturerId);
        course.setCapacity(60);
        course.setStatus(CourseStatus.PUBLISHED);
        courseRepository.save(course);

        mockMvc.perform(get("/courses?mine=true").header("Authorization", "Bearer " + tokenFor("LECTURER", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Course academic-year assignment ----

    @Test
    void assignCourseToAcademicYear_succeeds() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        String body = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String courseId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(post("/courses/" + courseId + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"semesterId\":\"" + activeSemester.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.academicYearId").value(activeYear.getId().toString()));

        mockMvc.perform(get("/courses/" + courseId + "/academic-years").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void assignCourseToAcademicYear_duplicateYear_isRejected() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        String body = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String courseId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(post("/courses/" + courseId + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/courses/" + courseId + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isConflict());
    }

    // ---- G-07: listAcademicYearAssignments was previously unguarded ----

    @Test
    void listAcademicYearAssignments_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        Course course = newCourse(CourseStatus.PUBLISHED);
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(get("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAcademicYearAssignments_asDeptHeadForOwnDepartment_succeeds() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated());

        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, program.getDepartmentId());
        mockMvc.perform(get("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- Academic Calendar: update/delete (years & semesters) ----

    @Test
    void updateYear_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/academic-years/" + activeYear.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2025/2026 (Revised)\",\"startDate\":\"2025-09-01\",\"endDate\":\"2026-06-30\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2025/2026 (Revised)"));
    }

    @Test
    void updateYear_toActive_doesNotDeactivateOtherActiveYears() throws Exception {
        String body = mockMvc.perform(post("/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2026/2027\",\"status\":\"UPCOMING\",\"createDefaultSemesters\":false}"))
                .andReturn().getResponse().getContentAsString();
        String newYearId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(put("/academic-years/" + newYearId)
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"2026/2027\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        activeYear = academicYearRepository.findByIdAndDeletedAtIsNull(activeYear.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(AcademicYearStatus.ACTIVE, activeYear.getStatus());
    }

    @Test
    void deleteYear_whenActive_isRejected() throws Exception {
        mockMvc.perform(delete("/academic-years/" + activeYear.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteYear_withDegreeProgramAssigned_isRejected() throws Exception {
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + archivedYear.getId() + "\",\"status\":\"DISCONTINUED\",\"courseIds\":[]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/academic-years/" + archivedYear.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteYear_withNoDependents_succeeds() throws Exception {
        mockMvc.perform(delete("/academic-years/" + archivedYear.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/academic-years").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updateSemester_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/academic-years/" + activeYear.getId() + "/semesters/" + activeSemester.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Semester 1 (Revised)\",\"sequence\":1,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Semester 1 (Revised)"));
    }

    @Test
    void deleteSemester_withCourseAssignment_isRejected() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        String body = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"catalogCourseId\":\"" + catalogCourse.getId() + "\",\"degreeProgramId\":\"" + program.getId() + "\"," +
                                "\"beginDate\":\"2025-09-01\",\"yearOfStudy\":1,\"capacity\":60,\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String courseId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        mockMvc.perform(post("/courses/" + courseId + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"semesterId\":\"" + activeSemester.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/academic-years/" + activeYear.getId() + "/semesters/" + activeSemester.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteSemester_withNoAssignments_succeeds() throws Exception {
        String body = mockMvc.perform(post("/academic-years/" + activeYear.getId() + "/semesters")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Semester 2\",\"sequence\":2,\"status\":\"UPCOMING\"}"))
                .andReturn().getResponse().getContentAsString();
        String semesterId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(delete("/academic-years/" + activeYear.getId() + "/semesters/" + semesterId)
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());
    }

    // ---- Reference Degree Details: update/delete ----

    @Test
    void updateDegreeProgram_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/degree-programs/" + program.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"BSc in Computer Science (Revised)\",\"durationYears\":4,\"totalCredits\":128,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BSc in Computer Science (Revised)"))
                .andExpect(jsonPath("$.totalCredits").value(128));
    }

    @Test
    void updateDegreeProgram_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(put("/degree-programs/" + program.getId())
                        .header("Authorization", "Bearer " + deptHeadToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Hijacked\",\"durationYears\":4,\"totalCredits\":120,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDegreeProgram_withCourses_isRejected() throws Exception {
        Course course = new Course();
        course.setCatalogCourse(catalogCourse);
        course.setDegreeProgram(program);
        course.setBeginDate(activeYear.getStartDate());
        course.setYearOfStudy(1);
        course.setLecturerId(lecturerId);
        course.setCapacity(30);
        course.setStatus(CourseStatus.PUBLISHED);
        courseRepository.save(course);

        mockMvc.perform(delete("/degree-programs/" + program.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteDegreeProgram_withNoDependents_succeeds() throws Exception {
        String body = mockMvc.perform(post("/degree-programs")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"code\":\"Dip-EMPTY\",\"name\":\"Diploma With Nothing\"," +
                                "\"departmentId\":\"" + UUID.randomUUID() + "\",\"durationYears\":2,\"totalCredits\":60,\"status\":\"ACTIVE\"}"))
                .andReturn().getResponse().getContentAsString();
        String newProgramId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(delete("/degree-programs/" + newProgramId)
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());
    }

    // ---- Degree Program <-> Academic Year assignment: course cascade, CRUD, all-years listing ----

    @Test
    void assignDegreeProgramToAcademicYear_byDefault_cascadesToEveryCourseInProgram() throws Exception {
        Course c1 = newCourse(CourseStatus.PUBLISHED);
        Course c2 = newCourse(CourseStatus.DRAFT);

        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/courses/" + c1.getId() + "/academic-years").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
        mockMvc.perform(get("/courses/" + c2.getId() + "/academic-years").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void assignDegreeProgramToAcademicYear_withExplicitCourseIds_onlyAssignsThose() throws Exception {
        Course c1 = newCourse(CourseStatus.PUBLISHED);
        Course c2 = newCourse(CourseStatus.PUBLISHED);

        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\",\"courseIds\":[\"" + c1.getId() + "\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/courses/" + c1.getId() + "/academic-years").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/courses/" + c2.getId() + "/academic-years").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listDegreeProgramAcademicYears_withNoYearParam_returnsAcrossAllYears() throws Exception {
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\",\"courseIds\":[]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + archivedYear.getId() + "\",\"status\":\"DISCONTINUED\",\"courseIds\":[]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/degree-program-academic-years").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateDegreeProgramAcademicYearAssignment_succeeds() throws Exception {
        String body = mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\",\"courseIds\":[]}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(put("/degree-programs/" + program.getId() + "/academic-years/" + assignmentId)
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"durationYears\":3,\"totalCredits\":100,\"status\":\"DISCONTINUED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.durationYears").value(3));
    }

    @Test
    void deleteDegreeProgramAcademicYearAssignment_succeeds() throws Exception {
        String body = mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\",\"courseIds\":[]}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(delete("/degree-programs/" + program.getId() + "/academic-years/" + assignmentId)
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/degree-program-academic-years").param("academicYearId", activeYear.getId().toString())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Course template: update/delete ----

    @Test
    void updateCourse_asOwningLecturer_succeeds() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.DRAFT);

        mockMvc.perform(put("/courses/" + course.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"beginDate\":\"2026-01-15\",\"yearOfStudy\":2,\"capacity\":45,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearOfStudy").value(2))
                .andExpect(jsonPath("$.capacity").value(45))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void updateCourse_asDifferentLecturer_isRejected() throws Exception {
        Course course = newCourse(CourseStatus.DRAFT);
        mockMvc.perform(put("/courses/" + course.getId())
                        .header("Authorization", "Bearer " + tokenFor("LECTURER", UUID.randomUUID()))
                        .contentType("application/json")
                        .content("{\"beginDate\":\"2026-01-15\",\"yearOfStudy\":2,\"capacity\":45,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCourse_cascadesItsAcademicYearAssignments() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/courses/" + course.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/" + course.getId() + "/academic-years").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteCourse_asAdmin_succeeds() throws Exception {
        Course course = newCourse(CourseStatus.DRAFT);
        mockMvc.perform(delete("/courses/" + course.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());
    }

    // ---- Course <-> Academic Year assignment: update/delete ----

    @Test
    void updateCourseAcademicYearAssignment_succeeds() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        String body = mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"semesterId\":\"" + activeSemester.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(put("/courses/" + course.getId() + "/academic-years/" + assignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"capacity\":15,\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(15))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void deleteCourseAcademicYearAssignment_succeeds() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        String body = mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(delete("/courses/" + course.getId() + "/academic-years/" + assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/" + course.getId() + "/academic-years").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateCourseAcademicYearAssignment_onceYearBecomesArchived_isRejected() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        String body = mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(put("/academic-years/" + activeYear.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"" + activeYear.getName() + "\",\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/courses/" + course.getId() + "/academic-years/" + assignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"capacity\":10,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict());
    }

    // ---- Per-year lecturer override (course_academic_years.lecturer_id) ----

    @Test
    void assignCourseToAcademicYear_withLecturerOverride_resolvesToOverride() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        UUID otherLecturer = UUID.randomUUID();

        mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"lecturerId\":\"" + otherLecturer + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecturerId").value(otherLecturer.toString()));
    }

    @Test
    void assignCourseToAcademicYear_withoutLecturerOverride_fallsBackToCourseDefault() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);

        mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lecturerId").value(lecturerId.toString()));
    }

    @Test
    void updateCourseAcademicYearAssignment_clearingLecturerOverride_fallsBackToCourseDefault() throws Exception {
        String token = tokenFor("LECTURER", lecturerId);
        Course course = newCourse(CourseStatus.PUBLISHED);
        String body = mockMvc.perform(post("/courses/" + course.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"lecturerId\":\"" + UUID.randomUUID() + "\",\"status\":\"PUBLISHED\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();

        mockMvc.perform(put("/courses/" + course.getId() + "/academic-years/" + assignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"capacity\":10,\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturerId").value(lecturerId.toString()));
    }

    // ---- "Degree Programs" per-row collapse: courses + resolved lecturer for that assignment ----

    @Test
    void listCoursesForAssignment_returnsCoursesWithResolvedLecturer() throws Exception {
        Course c1 = newCourse(CourseStatus.PUBLISHED);
        UUID overrideLecturer = UUID.randomUUID();

        String assignBody = mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(assignBody).get("id").asText();

        String cayBody = mockMvc.perform(get("/courses/" + c1.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        String cayId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cayBody).get(0).get("id").asText();

        mockMvc.perform(put("/courses/" + c1.getId() + "/academic-years/" + cayId)
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"capacity\":20,\"lecturerId\":\"" + overrideLecturer + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/degree-programs/" + program.getId() + "/academic-years/" + assignmentId + "/courses")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseId").value(c1.getId().toString()))
                .andExpect(jsonPath("$[0].catalogCourseCode").value("COSC5105"))
                .andExpect(jsonPath("$[0].lecturerId").value(overrideLecturer.toString()));
    }

    @Test
    void listCoursesForAssignment_asDeptHeadForDifferentDepartment_isRejected() throws Exception {
        newCourse(CourseStatus.PUBLISHED);
        String assignBody = mockMvc.perform(post("/degree-programs/" + program.getId() + "/academic-years")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"academicYearId\":\"" + activeYear.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(assignBody).get("id").asText();

        String deptHeadToken = tokenWithDepartmentAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, UUID.randomUUID());
        mockMvc.perform(get("/degree-programs/" + program.getId() + "/academic-years/" + assignmentId + "/courses")
                        .header("Authorization", "Bearer " + deptHeadToken))
                .andExpect(status().isForbidden());
    }

    // ---- G-09: per-assignment permission overrides are now consulted ----

    @Test
    void permissionOverride_grantsEditDeniedByBaselineRole_allowsAction() throws Exception {
        // LECTURER_ROLE_ID's baseline only grants "Degree Programs":"View" (see seed()) —
        // "Edit" is an unstubbed baseline denial. A per-assignment override granting the
        // resolved permission id should now take precedence over that baseline.
        UUID permissionId = UUID.randomUUID();
        when(roleCatalogClient.permissionIdOf(eq("Degree Programs"), eq("Edit"))).thenReturn(permissionId);

        String token = tokenWithAssignment("LECTURER", UUID.randomUUID(), LECTURER_ROLE_ID, "DEPARTMENT",
                program.getDepartmentId(), Map.of(permissionId, true));

        mockMvc.perform(put("/degree-programs/" + program.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Overridden Name\",\"durationYears\":4,\"totalCredits\":120,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Overridden Name"));
    }

    @Test
    void permissionOverride_revokesEditGrantedByBaselineRole_rejectsAction() throws Exception {
        // DEPT_HEAD_ROLE_ID's baseline grants every "Degree Programs" action (see seed()).
        // A per-assignment override revoking the resolved permission id should now take
        // precedence over that baseline grant.
        UUID permissionId = UUID.randomUUID();
        when(roleCatalogClient.permissionIdOf(eq("Degree Programs"), eq("Edit"))).thenReturn(permissionId);

        String token = tokenWithAssignment("LECTURER", UUID.randomUUID(), DEPT_HEAD_ROLE_ID, "DEPARTMENT",
                program.getDepartmentId(), Map.of(permissionId, false));

        mockMvc.perform(put("/degree-programs/" + program.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Should Not Apply\",\"durationYears\":4,\"totalCredits\":120,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    private Course newCourse(CourseStatus status) {
        Course course = new Course();
        course.setCatalogCourse(catalogCourse);
        course.setDegreeProgram(program);
        course.setBeginDate(activeYear.getStartDate());
        course.setYearOfStudy(1);
        course.setLecturerId(lecturerId);
        course.setCapacity(40);
        course.setStatus(status);
        return courseRepository.save(course);
    }
}
