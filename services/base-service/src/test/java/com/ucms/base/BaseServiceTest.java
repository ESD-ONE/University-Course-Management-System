package com.ucms.base;

import com.ucms.base.client.AcademicStructureClient;
import com.ucms.base.domain.CourseCategory;
import com.ucms.base.domain.Department;
import com.ucms.base.domain.Faculty;
import com.ucms.base.domain.GradingScale;
import com.ucms.base.domain.ReferenceStatus;
import com.ucms.base.repository.CourseCategoryRepository;
import com.ucms.base.repository.DepartmentRepository;
import com.ucms.base.repository.FacultyRepository;
import com.ucms.base.repository.GradingScaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Matches html-wireframes/index.html's Reference Data screen (adminReference(), lines
 * 1628-1662 + createRefEntry(), lines 989-1003): 3 tabs (faculties/grading/categories),
 * list + add entry, admin-only writes, reads open to any authenticated user.
 *
 * Seeds its own fixtures (mirroring V2__seed_data.sql's content) rather than relying on
 * Flyway, which is disabled for the H2 test profile — same pattern as the other services'
 * tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BaseServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired FacultyRepository facultyRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired GradingScaleRepository gradingScaleRepository;
    @Autowired CourseCategoryRepository courseCategoryRepository;
    @Autowired com.ucms.base.service.BaseService baseService;

    // Mocked so Department-delete tests don't make a real network call to
    // academic-structure-service (gap-analysis-report.md G-05) — matches this
    // repo's existing pattern for mocking a cross-service dependency (see
    // academic-structure-service's own tests mocking RoleCatalog).
    @MockBean AcademicStructureClient academicStructureClient;

    private Faculty computing;
    private Faculty engineering;
    private Department computerScience;
    private GradingScale passFail;
    private CourseCategory core;

    @BeforeEach
    void seed() {
        for (String[] fc : new String[][]{{"Faculty of Computing", "FOC"}, {"Faculty of Engineering", "FOE"}}) {
            Faculty f = new Faculty();
            f.setName(fc[0]);
            f.setCode(fc[1]);
            f.setStatus(ReferenceStatus.ACTIVE);
            f = facultyRepository.save(f);
            if (fc[1].equals("FOC")) computing = f; else engineering = f;
        }

        Department d = new Department();
        d.setFaculty(computing);
        d.setName("Department of Computer Science");
        d.setCode("DOCS");
        d.setStatus(ReferenceStatus.ACTIVE);
        computerScience = departmentRepository.save(d);

        GradingScale g = new GradingScale();
        g.setName("Pass / Fail");
        g.setBands("Pass: 50-100 · Fail: 0-49");
        g.setStatus(ReferenceStatus.ACTIVE);
        passFail = gradingScaleRepository.save(g);

        CourseCategory c = new CourseCategory();
        c.setName("Core");
        c.setDescription("Mandatory for program completion");
        c.setStatus(ReferenceStatus.ACTIVE);
        core = courseCategoryRepository.save(c);

        // Default: no dependents, so existing delete-succeeds tests keep working
        // unchanged. Individual tests override this where they need the "dependents
        // exist" branch.
        when(academicStructureClient.hasDependents(any())).thenReturn(false);
    }

    private String tokenFor(String role) {
        return KeycloakTestTokens.user(UUID.randomUUID(), role);
    }

    @Test
    void listFaculties_isReachableByAnyAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/faculties").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.code=='FOC')]").exists());
    }

    @Test
    void listFaculties_withNoToken_isRejected() throws Exception {
        mockMvc.perform(get("/faculties")).andExpect(status().isForbidden());
    }

    @Test
    void createFaculty_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/faculties")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Faculty of Science\",\"code\":\"FOS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FOS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/faculties").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void createFaculty_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(post("/faculties")
                        .header("Authorization", "Bearer " + tokenFor("LECTURER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Faculty of Science\",\"code\":\"FOS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFaculty_withDuplicateCode_isRejected() throws Exception {
        mockMvc.perform(post("/faculties")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Duplicate\",\"code\":\"FOC\"}"))
                .andExpect(status().isConflict());
    }

    // ---- Departments (docs/SYSTEM_ARCHITECTURE_REPORT.md Phase 1 / TC-P1-006..011) ----

    @Test
    void listDepartments_isReachableByAnyAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/departments").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("DOCS"))
                .andExpect(jsonPath("$[0].facultyName").value("Faculty of Computing"));
    }

    @Test
    void listDepartments_filteredByFacultyId_excludesOtherFaculties() throws Exception {
        mockMvc.perform(get("/departments")
                        .param("facultyId", computing.getId().toString())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/departments")
                        .param("facultyId", java.util.UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createDepartment_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/departments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Department of Mathematics\",\"code\":\"DOM\",\"facultyId\":\"" + computing.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("DOM"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createDepartment_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(post("/departments")
                        .header("Authorization", "Bearer " + tokenFor("LECTURER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Department of Mathematics\",\"code\":\"DOM\",\"facultyId\":\"" + computing.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDepartment_withDuplicateCode_isRejected() throws Exception {
        mockMvc.perform(post("/departments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Duplicate\",\"code\":\"DOCS\",\"facultyId\":\"" + computing.getId() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createDepartment_withMissingFaculty_returnsNotFound() throws Exception {
        mockMvc.perform(post("/departments")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Ghost Dept\",\"code\":\"GHOST\",\"facultyId\":\"" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createGradingScale_withDuplicateName_isRejected() throws Exception {
        mockMvc.perform(post("/grading-scales")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Pass / Fail\",\"bands\":\"Pass: 60-100 · Fail: 0-59\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateGradingScale_withDuplicateNameOfAnotherScale_isRejected() throws Exception {
        GradingScale other = new GradingScale();
        other.setName("Letter Grade Scale");
        other.setBands("A-F");
        other.setStatus(ReferenceStatus.ACTIVE);
        other = gradingScaleRepository.save(other);

        mockMvc.perform(put("/grading-scales/" + other.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Pass / Fail\",\"bands\":\"A-F\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listGradingScales_returnsSeededScale() throws Exception {
        mockMvc.perform(get("/grading-scales").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pass / Fail"));
    }

    @Test
    void createGradingScale_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/grading-scales")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"4.0 GPA Scale\",\"bands\":\"A: 4.0 ... F: 0.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("4.0 GPA Scale"));
    }

    @Test
    void createCourseCategory_withDuplicateName_isRejected() throws Exception {
        mockMvc.perform(post("/course-categories")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Core\",\"description\":\"Another core category\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateCourseCategory_withDuplicateNameOfAnotherCategory_isRejected() throws Exception {
        CourseCategory other = new CourseCategory();
        other.setName("Elective");
        other.setDescription("Optional courses");
        other.setStatus(ReferenceStatus.ACTIVE);
        other = courseCategoryRepository.save(other);

        mockMvc.perform(put("/course-categories/" + other.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Core\",\"description\":\"Optional courses\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listCourseCategories_returnsSeededCategory() throws Exception {
        mockMvc.perform(get("/course-categories").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Core"));
    }

    @Test
    void createCourseCategory_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/course-categories")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Capstone\",\"description\":\"Final-year project course\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Capstone"));
    }

    // ---- Faculty: update / delete ----

    @Test
    void updateFaculty_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/faculties/" + computing.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Faculty of Computing (Revised)\",\"code\":\"FOC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Faculty of Computing (Revised)"));
    }

    @Test
    void updateFaculty_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(put("/faculties/" + computing.getId())
                        .header("Authorization", "Bearer " + tokenFor("LECTURER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Hijacked\",\"code\":\"FOC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteFaculty_withDepartments_isRejected() throws Exception {
        mockMvc.perform(delete("/faculties/" + computing.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteFaculty_withNoDependents_succeeds() throws Exception {
        mockMvc.perform(delete("/faculties/" + engineering.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/faculties").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- Department: update / delete ----

    @Test
    void updateDepartment_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/departments/" + computerScience.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Department of CS (Revised)\",\"code\":\"DOCS\",\"facultyId\":\"" + computing.getId() + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Department of CS (Revised)"));
    }

    @Test
    void deleteDepartment_asAdmin_succeeds() throws Exception {
        // seed() already stubs academicStructureClient.hasDependents(any()) -> false
        mockMvc.perform(delete("/departments/" + computerScience.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/departments").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Department delete: cross-service dependents guard (gap-analysis-report.md G-05) ----

    @Test
    void deleteDepartment_withAcademicStructureDependents_isRejectedAndNotDeleted() throws Exception {
        when(academicStructureClient.hasDependents(computerScience.getId())).thenReturn(true);

        mockMvc.perform(delete("/departments/" + computerScience.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/departments").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deleteDepartment_withNoAcademicStructureDependents_succeeds() throws Exception {
        when(academicStructureClient.hasDependents(computerScience.getId())).thenReturn(false);

        mockMvc.perform(delete("/departments/" + computerScience.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/departments").header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteDepartment_whenDependentsCheckFails_isNotDeleted() {
        // Fail-closed: if the cross-service check itself can't be completed (connection
        // refused, timeout, 5xx, ...), the delete must NOT proceed. The mocked client
        // simulates that failure by throwing, same as the real RestClient would. Exercised
        // directly against the service (rather than through MockMvc) so the assertion is
        // about the exception propagating uncaught, not about how the servlet layer
        // happens to render it — in production this surfaces to the caller as a 5xx,
        // which is the acceptable/expected behavior per this fix's "fail closed" rule.
        when(academicStructureClient.hasDependents(computerScience.getId()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.client.ResourceAccessException.class,
                () -> baseService.deleteDepartment(computerScience.getId(), UUID.randomUUID()));

        org.junit.jupiter.api.Assertions.assertTrue(
                departmentRepository.findByIdAndDeletedAtIsNull(computerScience.getId()).isPresent(),
                "department must not be deleted when the dependents check itself failed");
    }

    // ---- Grading Scale: update / delete ----

    @Test
    void updateGradingScale_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/grading-scales/" + passFail.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Pass / Fail (Revised)\",\"bands\":\"Pass: 40-100\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pass / Fail (Revised)"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deleteGradingScale_asAdmin_succeeds() throws Exception {
        mockMvc.perform(delete("/grading-scales/" + passFail.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/grading-scales").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Course Category: update / delete ----

    @Test
    void updateCourseCategory_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/course-categories/" + core.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Core (Revised)\",\"description\":\"Updated\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Core (Revised)"));
    }

    @Test
    void deleteCourseCategory_asAdmin_succeeds() throws Exception {
        mockMvc.perform(delete("/course-categories/" + core.getId())
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/course-categories").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
