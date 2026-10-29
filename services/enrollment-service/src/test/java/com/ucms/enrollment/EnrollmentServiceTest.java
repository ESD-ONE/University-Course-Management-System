package com.ucms.enrollment;

import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.repository.CourseEnrollmentRepository;
import com.ucms.enrollment.repository.RegistrationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** flow-01 (student enrollment), flow-03 (admin registration), waitlist, roster. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnrollmentServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired RegistrationLogRepository registrationLogRepository;
    @MockBean AcademicClient academic;
    @MockBean IdentityClient identity;
    @MockBean EventPublisher events;

    private final UUID lecturerId = UUID.randomUUID();
    private final UUID otherLecturerId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID studentA = UUID.randomUUID();
    private final UUID studentB = UUID.randomUUID();
    private final UUID studentC = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();
    private final UUID yearId = UUID.randomUUID();
    private final Map<UUID, IdentityClient.User> users = new HashMap<>();
    private AcademicClient.Offering offering;

    @BeforeEach
    void seed() {
        offering = offering(UUID.randomUUID(), 2, true, "PUBLISHED");
        when(academic.getOffering(offering.id())).thenReturn(offering);
        when(academic.searchOfferings(any(), any(), any(), any())).thenReturn(List.of(offering));
        when(academic.getProgram(programId)).thenReturn(new AcademicClient.Program(programId, "BSC-CS",
                "BSc Computer Science", UUID.randomUUID(), 4, 120, "ACTIVE"));
        when(academic.listPrograms()).thenReturn(List.of(new AcademicClient.Program(programId, "BSC-CS",
                "BSc Computer Science", UUID.randomUUID(), 4, 120, "ACTIVE")));
        when(academic.listAcademicYears()).thenReturn(List.of(new AcademicClient.AcademicYear(yearId, "2025/2026", "ACTIVE")));

        users.put(studentA, new IdentityClient.User(studentA, "K. Perera", "k@uni.edu", "STUDENT", "ACTIVE", "STU-2025-0001"));
        users.put(studentB, new IdentityClient.User(studentB, "A. Jayawardena", "a@uni.edu", "STUDENT", "ACTIVE", "STU-2025-0002"));
        users.put(studentC, new IdentityClient.User(studentC, "C. Ranasinghe", "c@uni.edu", "STUDENT", "ACTIVE", "STU-2025-0003"));
        users.put(lecturerId, new IdentityClient.User(lecturerId, "Dr. N. Silva", "n@uni.edu", "LECTURER", "ACTIVE", null));
        users.forEach((id, u) -> when(identity.getUser(id)).thenReturn(u));
        when(identity.lookup(any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            Map<UUID, IdentityClient.User> found = new HashMap<>();
            ids.forEach(id -> { if (users.containsKey(id)) found.put(id, users.get(id)); });
            return found;
        });
    }

    private AcademicClient.Offering offering(UUID id, int capacity, boolean open, String status) {
        return new AcademicClient.Offering(id, UUID.randomUUID(), UUID.randomUUID(), "COSC5105", "Intro to Programming", 3,
                "CORE", programId, "BSC-CS", "BSc Computer Science", UUID.randomUUID(), 1, yearId, "2025/2026", "ACTIVE",
                UUID.randomUUID(), "Semester 1", null, null, capacity, lecturerId, status, open);
    }

    private String token(String role, UUID subject) {
        return KeycloakTestTokens.user(subject, role);
    }

    private String enrollBody(UUID offeringId, UUID studentId, boolean waitlist) {
        return "{\"offeringId\":\"" + offeringId + "\""
                + (studentId == null ? "" : ",\"studentId\":\"" + studentId + "\"")
                + ",\"joinWaitlistIfFull\":" + waitlist + "}";
    }

    private void enrollAs(UUID student) throws Exception {
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", student))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), null, false)))
                .andExpect(status().isCreated());
    }

    @Test
    void studentEnrollsSelf_isEnrolled_logged_andEventPublished() throws Exception {
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentA))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), null, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.courseCode").value("COSC5105"));

        verify(events).publish(eq("enrollment.student-enrolled"), anyMap());
        assertThat(registrationLogRepository.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getAction()).isEqualTo("COURSE_ENROLL"));
    }

    @Test
    void fullCourse_rejectsWith409CourseFull_unlessJoiningWaitlist() throws Exception {
        enrollAs(studentA);
        enrollAs(studentB);

        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentC))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), null, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_FULL"));

        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentC))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), null, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITLISTED"));
        verify(events).publish(eq("enrollment.student-waitlisted"), anyMap());
    }

    @Test
    void droppingAnEnrolledSeat_promotesTheFirstWaitlistedStudent() throws Exception {
        enrollAs(studentA);
        enrollAs(studentB);
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentC))
                .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), null, true)));

        UUID aEnrollment = courseEnrollmentRepository.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(studentA, offering.id())
                .orElseThrow().getId();
        mockMvc.perform(delete("/course-enrollments/" + aEnrollment).header("Authorization", "Bearer " + token("STUDENT", studentA)))
                .andExpect(status().isNoContent());

        assertThat(courseEnrollmentRepository.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(studentC, offering.id())
                .orElseThrow().getStatus().name()).isEqualTo("ENROLLED");
        verify(events).publish(eq("enrollment.waitlist-promoted"), anyMap());
    }

    @Test
    void enrollingTwice_isRejected() throws Exception {
        enrollAs(studentA);
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentA))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), null, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
    }

    @Test
    void studentCannotEnrollSomeoneElse() throws Exception {
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentA))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), studentB, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotDropSomeoneElsesEnrollment() throws Exception {
        enrollAs(studentA);
        UUID aEnrollment = courseEnrollmentRepository.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(studentA, offering.id())
                .orElseThrow().getId();
        mockMvc.perform(delete("/course-enrollments/" + aEnrollment).header("Authorization", "Bearer " + token("STUDENT", studentB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerCanRegisterStudentsOnlyIntoTheirOwnOffering() throws Exception {
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), studentA, false)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("LECTURER", otherLecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), studentB, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void closedEnrollmentWindow_blocksStudents_butAdminCanStillRegisterLate() throws Exception {
        AcademicClient.Offering closed = offering(UUID.randomUUID(), 10, false, "PUBLISHED");
        when(academic.getOffering(closed.id())).thenReturn(closed);

        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("STUDENT", studentA))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(closed.id(), null, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_OPEN"));
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(closed.id(), studentA, false)))
                .andExpect(status().isCreated());
    }

    @Test
    void draftOffering_cannotBeEnrolledEvenByAdmin() throws Exception {
        AcademicClient.Offering draft = offering(UUID.randomUUID(), 10, false, "DRAFT");
        when(academic.getOffering(draft.id())).thenReturn(draft);
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(draft.id(), studentA, false)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminRegistersStudentIntoProgram_andASecondActiveRegistrationIsRejected() throws Exception {
        String body = "{\"studentId\":\"" + studentA + "\",\"degreeProgramId\":\"" + programId
                + "\",\"intakeAcademicYearId\":\"" + yearId + "\",\"currentYearOfStudy\":1}";
        mockMvc.perform(post("/program-enrollments").header("Authorization", "Bearer " + token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.degreeProgramName").value("BSc Computer Science"));
        verify(events).publish(eq("enrollment.program-registered"), anyMap());

        mockMvc.perform(post("/program-enrollments").header("Authorization", "Bearer " + token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
    }

    @Test
    void programRegistration_isAdminOnly() throws Exception {
        String body = "{\"studentId\":\"" + studentA + "\",\"degreeProgramId\":\"" + programId
                + "\",\"intakeAcademicYearId\":\"" + yearId + "\",\"currentYearOfStudy\":1}";
        mockMvc.perform(post("/program-enrollments").header("Authorization", "Bearer " + token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(events, never()).publish(eq("enrollment.program-registered"), anyMap());
    }

    @Test
    void programRegistration_rejectsNonStudentAccounts() throws Exception {
        String body = "{\"studentId\":\"" + lecturerId + "\",\"degreeProgramId\":\"" + programId
                + "\",\"intakeAcademicYearId\":\"" + yearId + "\",\"currentYearOfStudy\":1}";
        mockMvc.perform(post("/program-enrollments").header("Authorization", "Bearer " + token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roster_isVisibleToTheOfferingsLecturer_notToOtherLecturers() throws Exception {
        enrollAs(studentA);
        mockMvc.perform(get("/course-enrollments").param("offeringId", offering.id().toString())
                        .header("Authorization", "Bearer " + token("LECTURER", lecturerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("K. Perera"))
                .andExpect(jsonPath("$[0].studentNumber").value("STU-2025-0001"));
        mockMvc.perform(get("/course-enrollments").param("offeringId", offering.id().toString())
                        .header("Authorization", "Bearer " + token("LECTURER", otherLecturerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalog_showsLiveSeatsAndTheCallersOwnStatus() throws Exception {
        enrollAs(studentA);
        mockMvc.perform(get("/catalog").header("Authorization", "Bearer " + token("STUDENT", studentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrolled").value(1))
                .andExpect(jsonPath("$[0].seatsLeft").value(1))
                .andExpect(jsonPath("$[0].myStatus").value("ENROLLED"))
                .andExpect(jsonPath("$[0].lecturerName").value("Dr. N. Silva"));
    }

    @Test
    void enrollmentCheck_isAdminOnly_andReportsEnrollment() throws Exception {
        enrollAs(studentA);
        mockMvc.perform(get("/course-enrollments/check").param("studentId", studentA.toString())
                        .param("offeringId", offering.id().toString())
                        .header("Authorization", "Bearer " + token("ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(true));
        mockMvc.perform(get("/course-enrollments/check").param("studentId", studentA.toString())
                        .param("offeringId", offering.id().toString())
                        .header("Authorization", "Bearer " + token("STUDENT", studentA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrations_recordWhoActedOnWhoseBehalf() throws Exception {
        mockMvc.perform(post("/course-enrollments").header("Authorization", "Bearer " + token("LECTURER", lecturerId))
                .contentType(MediaType.APPLICATION_JSON).content(enrollBody(offering.id(), studentA, false)));
        mockMvc.perform(get("/registrations").header("Authorization", "Bearer " + token("ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("K. Perera"))
                .andExpect(jsonPath("$[0].actorName").value("Dr. N. Silva"))
                .andExpect(jsonPath("$[0].actorRole").value("LECTURER"))
                .andExpect(jsonPath("$[0].targetLabel").value("COSC5105 — Intro to Programming"));
    }

    @Test
    void studentSearch_isAvailableToLecturers_butNotStudents() throws Exception {
        when(identity.searchStudents("per")).thenReturn(List.of(users.get(studentA)));
        mockMvc.perform(get("/students").param("q", "per").header("Authorization", "Bearer " + token("LECTURER", lecturerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("K. Perera"))
                .andExpect(jsonPath("$[0].studentNumber").value("STU-2025-0001"));
        mockMvc.perform(get("/students").header("Authorization", "Bearer " + token("STUDENT", studentA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/course-enrollments/me")).andExpect(status().isUnauthorized());
    }
}
