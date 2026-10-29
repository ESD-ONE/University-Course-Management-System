package com.ucms.grading;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.BaseClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.client.IdentityClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.repository.AssignmentRepository;
import com.ucms.grading.repository.SubmissionRepository;
import com.ucms.grading.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assignment lifecycle, submissions, and flow-02 grading. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssignmentGradingTest {

    @Autowired MockMvc mockMvc;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired SubmissionRepository submissionRepository;
    @MockBean AcademicClient academic;
    @MockBean EnrollmentClient enrollment;
    @MockBean IdentityClient identity;
    @MockBean BaseClient base;
    @MockBean FileStorage storage;
    @MockBean EventPublisher events;

    private final UUID lecturerId = UUID.randomUUID();
    private final UUID otherLecturerId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private AcademicClient.Offering offering;

    @BeforeEach
    void seed() {
        offering = new AcademicClient.Offering(UUID.randomUUID(), "COSC5105", "Intro to Programming", 3,
                UUID.randomUUID(), UUID.randomUUID(), "2025/2026", "ACTIVE", "Semester 1", 40, lecturerId, "PUBLISHED");
        when(academic.getOffering(offering.id())).thenReturn(offering);
        when(academic.searchOfferings(any(), any())).thenReturn(List.of(offering));
        when(enrollment.isEnrolled(studentId, offering.id())).thenReturn(true);
        when(enrollment.enrolledCounts(any())).thenReturn(Map.of(offering.id(), 2L));
        when(enrollment.enrolledStudents(offering.id())).thenReturn(List.of(
                new EnrollmentClient.RosterEntry(UUID.randomUUID(), studentId, "K. Perera", "STU-2025-0001", "ENROLLED"),
                new EnrollmentClient.RosterEntry(UUID.randomUUID(), strangerId, "D. Gunawardena", "STU-2025-0002", "ENROLLED")));
        when(enrollment.myEnrollments(anyString())).thenReturn(List.of(
                new EnrollmentClient.MyEnrollment(UUID.randomUUID(), offering.id(), "COSC5105", "Intro to Programming", "ENROLLED")));
        when(base.gradingScales()).thenReturn(List.of(new BaseClient.GradingScale("Standard Percentage",
                "A: 70-100 · B: 60-69 · C: 50-59 · D: 40-49 · F: 0-39", "ACTIVE")));
    }

    private String token(String role, UUID subject) {
        return "Bearer " + KeycloakTestTokens.user(subject, role);
    }

    private Assignment assignment(Assignment.Status status, OffsetDateTime due) {
        Assignment a = new Assignment();
        a.setCourseOfferingId(offering.id());
        a.setTitle("Programming Exercise 2");
        a.setDescription("Command-line calculator");
        a.setDueDate(due);
        a.setMaxMarks(100);
        a.setStatus(status);
        return assignmentRepository.save(a);
    }

    private MockMultipartFile zip(String name) {
        return new MockMultipartFile("file", name, "application/zip", "PK...".getBytes());
    }

    private String submit(Assignment a) throws Exception {
        String body = mockMvc.perform(multipart("/assignments/" + a.getId() + "/submissions").file(zip("ex2_calc.zip"))
                        .header("Authorization", token("STUDENT", studentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"submissionId\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void lecturerCreatesDraftAssignment_forOwnCourseOnly() throws Exception {
        String body = "{\"offeringId\":\"" + offering.id() + "\",\"title\":\"Exercise 3\",\"dueDate\":\""
                + OffsetDateTime.now().plusDays(7) + "\",\"maxMarks\":50}";
        mockMvc.perform(post("/assignments").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.courseCode").value("COSC5105"));
        mockMvc.perform(post("/assignments").header("Authorization", token("LECTURER", otherLecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/assignments").header("Authorization", token("STUDENT", studentId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishing_emitsEvent_andOnlyThenStudentsSeeIt() throws Exception {
        Assignment a = assignment(Assignment.Status.DRAFT, OffsetDateTime.now().plusDays(3));
        mockMvc.perform(get("/assignments").header("Authorization", token("STUDENT", studentId)))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(patch("/assignments/" + a.getId() + "/status").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());
        verify(events).publish(eq("grading.assignment-published"), anyMap());

        mockMvc.perform(get("/assignments").header("Authorization", token("STUDENT", studentId)))
                .andExpect(jsonPath("$[0].title").value("Programming Exercise 2"))
                .andExpect(jsonPath("$[0].mySubmissionStatus").value("NOT_SUBMITTED"));
    }

    @Test
    void studentSubmits_fileGoesToStorage_andLecturerIsNotified() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        submit(a);
        verify(storage).put(anyString(), any(), anyLong(), eq("application/zip"));
        verify(events).publish(eq("grading.submission-received"), anyMap());
        mockMvc.perform(get("/assignments/" + a.getId() + "/submissions/me").header("Authorization", token("STUDENT", studentId)))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.fileName").value("ex2_calc.zip"));
    }

    @Test
    void submittingAfterTheDeadline_isRejected() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().minusHours(1));
        mockMvc.perform(multipart("/assignments/" + a.getId() + "/submissions").file(zip("late.zip"))
                        .header("Authorization", token("STUDENT", studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAST_DUE"));
        verify(storage, never()).put(anyString(), any(), anyLong(), any());
    }

    @Test
    void studentNotEnrolled_cannotSubmit() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        mockMvc.perform(multipart("/assignments/" + a.getId() + "/submissions").file(zip("x.zip"))
                        .header("Authorization", token("STUDENT", UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void resubmittingReplaces_butNotAfterGrading() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        String submissionId = submit(a);
        submit(a);
        org.assertj.core.api.Assertions.assertThat(submissionRepository.findByAssignmentId(a.getId())).hasSize(1);

        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":92,\"feedback\":\"Great work\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/assignments/" + a.getId() + "/submissions").file(zip("again.zip"))
                        .header("Authorization", token("STUDENT", studentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_GRADED"));
    }

    @Test
    void grading_confirmsEnrollment_validatesMarks_andPublishesGradePosted() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        String submissionId = submit(a);

        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":120}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", otherLecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":80}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":88,\"feedback\":\"Good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marks").value(88));
        verify(events).publish(eq("grading.grade-posted"), anyMap());

        when(enrollment.isEnrolled(studentId, offering.id())).thenReturn(false);
        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":90}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
    }

    @Test
    void gradingScreen_listsEveryEnrolledStudent_includingNonSubmitters() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        submit(a);
        mockMvc.perform(get("/assignments/" + a.getId() + "/submissions").header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.studentName=='D. Gunawardena')].status").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$[?(@.studentName=='K. Perera')].status").value("SUBMITTED"));
    }

    @Test
    void bulkSaveAllGrades_gradesEverySubmission() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        String submissionId = submit(a);
        mockMvc.perform(put("/assignments/" + a.getId() + "/grades").header("Authorization", token("LECTURER", lecturerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"submissionId\":\"" + submissionId + "\",\"marks\":75}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marks").value(75));
    }

    @Test
    void studentGradeHistory_computesPercentageLetterAndGpa() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        String submissionId = submit(a);
        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", lecturerId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":88}"));

        mockMvc.perform(get("/grades/me").header("Authorization", token("STUDENT", studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].courseCode").value("COSC5105"))
                .andExpect(jsonPath("$.courses[0].percentage").value(88.0))
                .andExpect(jsonPath("$.courses[0].letter").value("A"))
                .andExpect(jsonPath("$.gpa").value(4.0))
                .andExpect(jsonPath("$.creditsGraded").value(3));
    }

    @Test
    void deletingAnAssignmentWithSubmissions_isBlocked() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        submit(a);
        mockMvc.perform(delete("/assignments/" + a.getId()).header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(status().isConflict());
        Assignment empty = assignment(Assignment.Status.DRAFT, OffsetDateTime.now().plusDays(3));
        mockMvc.perform(delete("/assignments/" + empty.getId()).header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void offeringSummary_reportsClassAverage() throws Exception {
        Assignment a = assignment(Assignment.Status.OPEN, OffsetDateTime.now().plusDays(3));
        String submissionId = submit(a);
        mockMvc.perform(put("/submissions/" + submissionId + "/grade").header("Authorization", token("LECTURER", lecturerId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":70}"));
        mockMvc.perform(get("/grades/offerings/" + offering.id() + "/summary").header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(jsonPath("$.classAverage").value(70.0))
                .andExpect(jsonPath("$.graded").value(1))
                .andExpect(jsonPath("$.totalStudents").value(2));
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/assignments")).andExpect(status().isUnauthorized());
    }
}
