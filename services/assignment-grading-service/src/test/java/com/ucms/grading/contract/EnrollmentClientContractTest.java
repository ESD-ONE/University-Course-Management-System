package com.ucms.grading.contract;

import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.security.ClientCredentialsTokenProvider;
import com.ucms.grading.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real EnrollmentClient against a stub of enrollment-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:enrollment-service",
        repositoryRoot = "stubs://file://${user.dir}/../enrollment-service/src/test/resources/contracts/assignment-grading-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class EnrollmentClientContractTest {

    private static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Autowired EnrollmentClient enrollment;
    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;
    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void isEnrolled_readsTheEnrolledFlag() {
        assertThat(enrollment.isEnrolled(STUDENT_ID, OFFERING_ID)).isTrue();
    }

    @Test
    void enrolledStudents_readsThePageContent() {
        assertThat(enrollment.enrolledStudents(OFFERING_ID)).containsExactly(new EnrollmentClient.RosterEntry(
                ENROLLMENT_ID, STUDENT_ID, "K. Perera", "STU-2025-0001", "ENROLLED"));
    }

    @Test
    void enrolledCounts_readsTheEnrolledCountPerOffering() {
        assertThat(enrollment.enrolledCounts(List.of(OFFERING_ID))).isEqualTo(Map.of(OFFERING_ID, 42L));
    }

    @Test
    void myEnrollments_readsTheCallersCourses() {
        assertThat(enrollment.myEnrollments("user-token")).containsExactly(new EnrollmentClient.MyEnrollment(
                ENROLLMENT_ID, OFFERING_ID, "COSC5105", "Introduction to Programming", "ENROLLED"));
    }
}
