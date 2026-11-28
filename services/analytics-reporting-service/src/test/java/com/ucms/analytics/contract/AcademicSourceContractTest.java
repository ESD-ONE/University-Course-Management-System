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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real SourceClients against a stub of academic-structure-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:academic-structure-service",
        repositoryRoot = "stubs://file://${user.dir}/../academic-structure-service/src/test/resources/contracts/analytics-reporting-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class AcademicSourceContractTest {

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID LECTURER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Autowired SourceClients sources;

    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;

    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void offerings_readsEveryFieldTheReportsUse() {
        assertThat(sources.offerings(null)).containsExactly(new SourceClients.Offering(OFFERING_ID, "COSC5105",
                "Introduction to Programming", 3, PROGRAM_ID, "BSc in Computer Science", "2025/2026", "ACTIVE",
                "Semester 1", 60, LECTURER_ID, "PUBLISHED"));
    }

    @Test
    void programs_readsThePageContent() {
        assertThat(sources.programs()).containsExactly(
                new SourceClients.Program(PROGRAM_ID, "BSC-CS", "BSc in Computer Science", 120, "ACTIVE"));
    }
}
