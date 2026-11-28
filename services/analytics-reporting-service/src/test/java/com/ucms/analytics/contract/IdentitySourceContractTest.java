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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real SourceClients against a stub of identity-access-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:identity-access-service",
        repositoryRoot = "stubs://file://${user.dir}/../identity-access-service/src/test/resources/contracts/analytics-reporting-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class IdentitySourceContractTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final SourceClients.User USER = new SourceClients.User(USER_ID, "K. Perera", "k.perera@uni.edu",
            "STUDENT", "ACTIVE", "STU-2025-0001");

    @Autowired SourceClients sources;
    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;
    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void userSummary_readsTheFourCounts() {
        assertThat(sources.userSummary()).isEqualTo(new SourceClients.UserSummary(120, 110, 100, 20));
    }

    @Test
    void allUsers_readsThePageContentAndStopsAtTheLastPage() {
        assertThat(sources.allUsers()).containsExactly(USER);
    }

    @Test
    void lookupUsers_readsTheUsersAndKeysThemById() {
        assertThat(sources.lookupUsers(List.of(USER_ID))).isEqualTo(Map.of(USER_ID, USER));
    }
}
