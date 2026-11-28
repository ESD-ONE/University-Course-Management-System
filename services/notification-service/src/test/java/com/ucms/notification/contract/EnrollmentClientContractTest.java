package com.ucms.notification.contract;

import com.ucms.notification.client.EnrollmentClient;
import com.ucms.notification.security.ClientCredentialsTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

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
        repositoryRoot = "stubs://file://${user.dir}/../enrollment-service/src/test/resources/contracts/notification-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class EnrollmentClientContractTest {

    private static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired EnrollmentClient enrollment;
    @MockBean ClientCredentialsTokenProvider serviceTokens;

    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void enrolledStudentIds_readsTheStudentsOfAnOffering() {
        assertThat(enrollment.enrolledStudentIds(OFFERING_ID)).containsExactly(STUDENT_ID);
    }
}
