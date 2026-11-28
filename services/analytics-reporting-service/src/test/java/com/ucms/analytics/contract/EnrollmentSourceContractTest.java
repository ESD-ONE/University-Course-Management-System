package com.ucms.analytics.contract;

import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.security.ClientCredentialsTokenProvider;
import com.ucms.analytics.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real SourceClients against a stub of enrollment-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:enrollment-service",
        repositoryRoot = "stubs://file://${user.dir}/../enrollment-service/src/test/resources/contracts/analytics-reporting-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class EnrollmentSourceContractTest {

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired SourceClients sources;
    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;
    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void myEnrollments_readsTheCallersCourses() {
        assertThat(sources.myEnrollments("user-token")).containsExactly(new SourceClients.MyEnrollment(OFFERING_ID,
                "COSC5105", "Introduction to Programming", 3, "CORE", "Dr. N. Silva", "2025/2026", "Semester 1",
                "ENROLLED"));
    }

    @Test
    void myPrograms_readsTheCallersDegreePrograms() {
        assertThat(sources.myPrograms("user-token")).containsExactly(new SourceClients.MyProgram(PROGRAM_ID, "BSC-CS",
                "BSc in Computer Science", 120, 4, 1, "2025/2026", "ACTIVE"));
    }

    @Test
    void enrollmentStats_readsTheTotalsAndTheirBreakdowns() {
        assertThat(sources.enrollmentStats()).isEqualTo(new SourceClients.EnrollmentStats(5, 42, 3,
                List.of(new SourceClients.ProgramCount(PROGRAM_ID, "BSc in Computer Science", 5)),
                List.of(new SourceClients.OfferingCount(OFFERING_ID, 42, 3)),
                List.of(new SourceClients.MonthCount("2025-09", 12))));
    }

    @Test
    void registrations_readsTheLatestRegistrations() {
        assertThat(sources.registrations(5)).containsExactly(new SourceClients.Registration(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), "K. Perera", "STU-2025-0001", "COURSE",
                "COSC5105 Introduction to Programming", "A. Admin", "ADMIN", "ENROLLED",
                OffsetDateTime.parse("2025-09-02T08:30:00Z")));
    }
}
