package com.ucms.enrollment;

import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.domain.CourseEnrollmentStatus;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.repository.CourseEnrollmentRepository;
import com.ucms.enrollment.repository.RegistrationLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Seat and waitlist rules under requests that arrive at the same moment. SeatLock is a
 * PostgreSQL advisory lock and a no-op on H2, so only a test on PostgreSQL can show that
 * a course is never overfilled. Not @Transactional: each request must commit on its own
 * connection, as it does in production.
 */
@AutoConfigureMockMvc
class EnrollmentConcurrencyIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired CourseEnrollmentRepository enrollments;
    @Autowired RegistrationLogRepository registrationLog;
    @MockBean AcademicClient academic;
    @MockBean IdentityClient identity;
    @MockBean EventPublisher events;

    private UUID offeringId;

    private record Response(int status, String body) {}

    @BeforeEach
    void anyIdIsAnActiveStudent() {
        when(identity.getUser(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new IdentityClient.User(id, "Student " + id, id + "@uni.edu", "STUDENT", "ACTIVE", "STU-" + id);
        });
    }

    @AfterEach
    void cleanUp() {
        enrollments.deleteAll();
        registrationLog.deleteAll();
    }

    @Test
    void twentyStudentsRaceForTheLastSeat_exactlyOneGetsIt() throws Exception {
        openOffering(1);

        List<Response> responses = atTheSameTime(students(20).stream().map(s -> enroll(s, false)).toList());

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 409 && r.body().contains("COURSE_FULL")).hasSize(19);
        assertThat(count(CourseEnrollmentStatus.ENROLLED)).isEqualTo(1);
    }

    @Test
    void racingForSeatsWithWaitlist_neverOverfillsTheCourse() throws Exception {
        openOffering(2);

        List<Response> responses = atTheSameTime(students(10).stream().map(s -> enroll(s, true)).toList());

        assertThat(responses).allMatch(r -> r.status() == 201);
        assertThat(count(CourseEnrollmentStatus.ENROLLED)).isEqualTo(2);
        assertThat(count(CourseEnrollmentStatus.WAITLISTED)).isEqualTo(8);
    }

    @Test
    void theSameStudentEnrollingTwiceAtOnce_getsOneEnrollment() throws Exception {
        openOffering(5);
        UUID student = UUID.randomUUID();

        List<Response> responses = atTheSameTime(List.of(enroll(student, false), enroll(student, false)));

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 409 && r.body().contains("ALREADY_ENROLLED")).hasSize(1);
        assertThat(count(CourseEnrollmentStatus.ENROLLED)).isEqualTo(1);
    }

    @Test
    void twoDropsAtOnce_promoteTwoWaitlistedStudents_eachOnce() throws Exception {
        openOffering(2);
        List<UUID> enrolled = students(2);
        List<UUID> waiting = students(2);
        for (UUID s : enrolled) enroll(s, false).call();
        for (UUID s : waiting) enroll(s, true).call();

        List<Response> responses = atTheSameTime(enrolled.stream().map(this::drop).toList());

        assertThat(responses).allMatch(r -> r.status() == 204);
        assertThat(waiting).allSatisfy(s -> assertThat(statusOf(s)).isEqualTo(CourseEnrollmentStatus.ENROLLED));
        assertThat(count(CourseEnrollmentStatus.ENROLLED)).isEqualTo(2);
        assertThat(count(CourseEnrollmentStatus.WAITLISTED)).isZero();
    }

    @Test
    void aDropPromotesTheLongestWaitingStudent() throws Exception {
        openOffering(1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        enroll(first, false).call();
        enroll(second, true).call();
        enroll(third, true).call();

        assertThat(drop(first).call().status()).isEqualTo(204);

        assertThat(statusOf(second)).isEqualTo(CourseEnrollmentStatus.ENROLLED);
        assertThat(statusOf(third)).isEqualTo(CourseEnrollmentStatus.WAITLISTED);
    }

    private void openOffering(int capacity) {
        offeringId = UUID.randomUUID();
        AcademicClient.Offering offering = new AcademicClient.Offering(offeringId, UUID.randomUUID(), UUID.randomUUID(),
                "COSC5105", "Intro to Programming", 3, "CORE", UUID.randomUUID(), "BSC-CS", "BSc Computer Science",
                UUID.randomUUID(), 1, UUID.randomUUID(), "2025/2026", "ACTIVE", UUID.randomUUID(), "Semester 1", null,
                null, capacity, UUID.randomUUID(), "PUBLISHED", true);
        when(academic.getOffering(offeringId)).thenReturn(offering);
    }

    private static List<UUID> students(int howMany) {
        return IntStream.range(0, howMany).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private Callable<Response> enroll(UUID student, boolean joinWaitlistIfFull) {
        String body = "{\"offeringId\":\"" + offeringId + "\",\"joinWaitlistIfFull\":" + joinWaitlistIfFull + "}";
        return () -> response(mockMvc.perform(post("/course-enrollments")
                .header("Authorization", "Bearer " + KeycloakTestTokens.user(student, "STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn());
    }

    private Callable<Response> drop(UUID student) {
        return () -> {
            UUID enrollmentId = enrollments.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(student, offeringId)
                    .orElseThrow().getId();
            return response(mockMvc.perform(delete("/course-enrollments/" + enrollmentId)
                    .header("Authorization", "Bearer " + KeycloakTestTokens.user(student, "STUDENT"))).andReturn());
        };
    }

    private static Response response(MvcResult result) throws Exception {
        return new Response(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    /** Runs every request on its own thread, all released at the same instant. */
    private static List<Response> atTheSameTime(List<Callable<Response>> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<Response> request : requests) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) responses.add(future.get(60, TimeUnit.SECONDS));
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private long count(CourseEnrollmentStatus status) {
        return enrollments.countByCourseOfferingIdAndStatusAndDeletedAtIsNull(offeringId, status);
    }

    private CourseEnrollmentStatus statusOf(UUID student) {
        return enrollments.findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(student, offeringId)
                .orElseThrow().getStatus();
    }
}
