package com.ucms.notification.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.notification.client.EnrollmentClient;
import com.ucms.notification.client.IdentityClient;
import com.ucms.notification.domain.Notification;
import com.ucms.notification.domain.Notification.Category;
import com.ucms.notification.messaging.EventListener;
import com.ucms.notification.repository.NotificationRepository;
import com.ucms.notification.service.EmailSender;
import com.ucms.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gives the real EventListener each event exactly as its publisher's contract describes
 * it. The contract files live in the publishing service
 * (src/test/resources/contracts/events/notification-service), and that service's build
 * checks them against what it really publishes. So if a publisher renames or drops a
 * field that a notification is built from, one of the two sides fails.
 *
 * No broker is involved: delivery over RabbitMQ is covered by EventConsumerIT.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventContractTest {

    private static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LECTURER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired NotificationService notifications;
    @Autowired NotificationRepository repository;
    @Autowired ObjectMapper objectMapper;
    @MockBean IdentityClient identity;
    @MockBean EnrollmentClient enrollment;
    @MockBean EmailSender email;

    private EventListener listener;

    @BeforeEach
    void setUp() {
        listener = new EventListener(notifications, objectMapper);
        when(enrollment.enrolledStudentIds(OFFERING_ID)).thenReturn(List.of(STUDENT_ID));
    }

    @Test
    void studentEnrolled_tellsTheStudentWhichCourse() throws Exception {
        deliver("enrollment-service", "enrollment.student-enrolled");

        assertNotification(STUDENT_ID, Category.REGISTRATION, "Enrolled in COSC5105",
                "You're enrolled in COSC5105 — Introduction to Programming.");
    }

    @Test
    void studentWaitlisted_tellsTheStudentWhichCourse() throws Exception {
        deliver("enrollment-service", "enrollment.student-waitlisted");

        assertNotification(STUDENT_ID, Category.REGISTRATION, "Waitlisted for COSC5105",
                "COSC5105 is full — you're on the waitlist and will be enrolled automatically if a seat opens.");
    }

    @Test
    void waitlistPromoted_tellsTheStudentWhichCourse() throws Exception {
        deliver("enrollment-service", "enrollment.waitlist-promoted");

        assertNotification(STUDENT_ID, Category.REGISTRATION, "A seat opened in COSC5105",
                "You've been moved off the waitlist and are now enrolled in COSC5105 — Introduction to Programming.");
    }

    @Test
    void courseDropped_tellsTheStudentWhichCourse() throws Exception {
        deliver("enrollment-service", "enrollment.course-dropped");

        assertNotification(STUDENT_ID, Category.REGISTRATION, "Dropped COSC5105",
                "You're no longer enrolled in COSC5105 — Introduction to Programming.");
    }

    @Test
    void programRegistered_tellsTheStudentWhichProgram() throws Exception {
        deliver("enrollment-service", "enrollment.program-registered");

        assertNotification(STUDENT_ID, Category.REGISTRATION, "Registered in BSc in Computer Science",
                "You've been registered in BSc in Computer Science (BSC-CS).");
    }

    @Test
    void assignmentPublished_tellsEachStudentOfTheOffering() throws Exception {
        deliver("assignment-grading-service", "grading.assignment-published");

        assertNotification(STUDENT_ID, Category.ASSIGNMENT, "New assignment: Essay 1", "COSC5105 · due 2025-10-01T17:00Z");
    }

    @Test
    void submissionReceived_tellsTheLecturer() throws Exception {
        deliver("assignment-grading-service", "grading.submission-received");

        assertNotification(LECTURER_ID, Category.ASSIGNMENT, "New submission: Essay 1",
                "A student submitted work for COSC5105 · Essay 1.");
    }

    @Test
    void gradePosted_tellsTheStudentTheirMarks() throws Exception {
        deliver("assignment-grading-service", "grading.grade-posted");

        assertNotification(STUDENT_ID, Category.GRADE, "Grade posted: Essay 1", "COSC5105 — you scored 92 / 100.");
    }

    @Test
    void userCreated_welcomesTheNewUserByName() throws Exception {
        deliver("identity-access-service", "identity.user-created");

        assertNotification(STUDENT_ID, Category.SYSTEM, "Welcome to UCMS", "Hi K. Perera, your account is ready.");
    }

    @Test
    void reportGenerated_emailsTheRecipientsOfTheSchedule() throws Exception {
        deliver("analytics-reporting-service", "analytics.report-generated");

        verify(email).send(eq("registrar@uni.edu"), eq("[UCMS] Scheduled report: Course Pass Rate"),
                contains("was generated at 2025-09-02T06:00:00Z"));
    }

    /** Reads the event from the publisher's contract file and hands it to the listener. */
    @SuppressWarnings("unchecked")
    private void deliver(String publisher, String eventType) throws Exception {
        Path contract = Path.of("..", publisher, "src/test/resources/contracts/events/notification-service",
                eventType + ".yml");
        Map<String, Object> yaml;
        try (Reader reader = Files.newBufferedReader(contract)) {
            yaml = new Yaml().load(reader);
        }
        Object event = ((Map<String, Object>) yaml.get("outputMessage")).get("body");
        listener.onEvent(objectMapper.writeValueAsString(event));
    }

    private void assertNotification(UUID userId, Category category, String title, String body) {
        assertThat(repository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getUserId()).isEqualTo(userId);
            assertThat(notification.getCategory()).isEqualTo(category);
            assertThat(notification.getTitle()).isEqualTo(title);
            assertThat(notification.getBody()).isEqualTo(body);
        });
    }
}
