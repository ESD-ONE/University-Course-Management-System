package com.ucms.notification;

import com.ucms.notification.client.EnrollmentClient;
import com.ucms.notification.client.IdentityClient;
import com.ucms.notification.repository.NotificationRepository;
import com.ucms.notification.service.EmailSender;
import com.ucms.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Event → notification mapping, preferences, dedupe, and the per-user REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired NotificationService notifications;
    @Autowired NotificationRepository repository;
    @MockBean IdentityClient identity;
    @MockBean EnrollmentClient enrollment;
    @MockBean EmailSender email;

    private final UUID student = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void seed() {
        when(identity.getUser(student)).thenReturn(new IdentityClient.User(student, "K. Perera", "k@uni.edu", "STUDENT", "ACTIVE"));
        when(identity.getUser(otherStudent)).thenReturn(new IdentityClient.User(otherStudent, "A. J", "a@uni.edu", "STUDENT", "ACTIVE"));
        when(identity.profile(anyString())).thenReturn(new IdentityClient.Profile(false, true, false, true));
        when(enrollment.enrolledStudentIds(offeringId)).thenReturn(List.of(student, otherStudent));
    }

    private String token(UUID subject) {
        return "Bearer " + KeycloakTestTokens.user(subject, "STUDENT");
    }

    private Map<String, Object> enrolled() {
        return Map.of("studentId", student.toString(), "offeringId", offeringId.toString(),
                "courseCode", "COSC5105", "courseName", "Intro to Programming", "onBehalf", false);
    }

    @Test
    void studentEnrolled_createsInAppNotification_andEmails() throws Exception {
        notifications.handle(UUID.randomUUID(), "enrollment.student-enrolled", enrolled());

        mockMvc.perform(get("/notifications/me").header("Authorization", token(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Enrolled in COSC5105"))
                .andExpect(jsonPath("$.items[0].category").value("REGISTRATION"))
                .andExpect(jsonPath("$.unreadCount").value(1));
        // Email goes out only after the notification commits.
        verify(email, never()).send(anyString(), anyString(), anyString());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(email).send(eq("k@uni.edu"), eq("[UCMS] Enrolled in COSC5105"), anyString());
    }

    @Test
    void redeliveredEvent_doesNotDuplicate() {
        UUID eventId = UUID.randomUUID();
        notifications.handle(eventId, "enrollment.student-enrolled", enrolled());
        notifications.handle(eventId, "enrollment.student-enrolled", enrolled());
        assertThat(repository.countByUserIdAndReadAtIsNull(student)).isEqualTo(1);
    }

    @Test
    void assignmentPublished_fansOutToEveryEnrolledStudent() {
        notifications.handle(UUID.randomUUID(), "grading.assignment-published", Map.of(
                "offeringId", offeringId.toString(), "courseCode", "COSC5105", "title", "Exercise 2", "dueDate", "2026-10-14"));
        assertThat(repository.countByUserIdAndReadAtIsNull(student)).isEqualTo(1);
        assertThat(repository.countByUserIdAndReadAtIsNull(otherStudent)).isEqualTo(1);
    }

    @Test
    void turnedOffCategory_suppressesTheNotification_andEmailOptOutSuppressesEmail() throws Exception {
        // First visit seeds from the legacy profile: email off, grades off.
        mockMvc.perform(get("/preferences/me").header("Authorization", token(student)))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.gradeAlerts").value(false));

        notifications.handle(UUID.randomUUID(), "grading.grade-posted", Map.of(
                "studentId", student.toString(), "courseCode", "COSC5105", "title", "Exercise 2", "marks", "88", "maxMarks", 100));
        assertThat(repository.countByUserIdAndReadAtIsNull(student)).isZero();

        notifications.handle(UUID.randomUUID(), "enrollment.student-enrolled", enrolled());
        assertThat(repository.countByUserIdAndReadAtIsNull(student)).isEqualTo(1);
        verify(email, never()).send(eq("k@uni.edu"), anyString(), anyString());
    }

    @Test
    void updatingPreferences_persists() throws Exception {
        mockMvc.perform(put("/preferences/me").header("Authorization", token(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true,\"assignmentAlerts\":false,\"gradeAlerts\":true,\"registrationAlerts\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentAlerts").value(false));
        mockMvc.perform(get("/preferences/me").header("Authorization", token(student)))
                .andExpect(jsonPath("$.assignmentAlerts").value(false));
    }

    @Test
    void markRead_onlyForOwnNotifications_andReadAllClearsUnread() throws Exception {
        notifications.handle(UUID.randomUUID(), "enrollment.student-enrolled", enrolled());
        UUID id = repository.findByUserIdOrderByCreatedAtDesc(student, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();

        mockMvc.perform(patch("/notifications/" + id + "/read").header("Authorization", token(otherStudent)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/notifications/" + id + "/read").header("Authorization", token(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        notifications.handle(UUID.randomUUID(), "enrollment.course-dropped", enrolled());
        mockMvc.perform(post("/notifications/me/read-all").header("Authorization", token(student)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", token(student)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void scheduledReport_emailsEachRecipient() {
        notifications.handle(UUID.randomUUID(), "analytics.report-generated", Map.of(
                "reportName", "Enrollment Summary", "recipients", "a@uni.edu, b@uni.edu", "generatedAt", "now"));
        verify(email).send(eq("a@uni.edu"), anyString(), anyString());
        verify(email).send(eq("b@uni.edu"), anyString(), anyString());
    }

    @Test
    void unknownEventTypes_areIgnored() {
        notifications.handle(UUID.randomUUID(), "something.else", Map.of("studentId", student.toString()));
        assertThat(repository.countByUserIdAndReadAtIsNull(student)).isZero();
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/notifications/me")).andExpect(status().isUnauthorized());
    }
}
