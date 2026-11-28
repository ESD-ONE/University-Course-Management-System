package com.ucms.enrollment.contract;

import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.security.ClientCredentialsTokenProvider;
import jakarta.persistence.EntityNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Runs the real IdentityClient against a stub of identity-access-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:identity-access-service",
        repositoryRoot = "stubs://file://${user.dir}/../identity-access-service/src/test/resources/contracts/enrollment-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class IdentityClientContractTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID UNKNOWN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final IdentityClient.User USER = new IdentityClient.User(USER_ID, "K. Perera", "k.perera@uni.edu",
            "STUDENT", "ACTIVE", "STU-2025-0001");

    @Autowired IdentityClient identity;
    @MockBean ClientCredentialsTokenProvider serviceTokens;

    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void getUser_readsEveryFieldTheServiceUses() {
        assertThat(identity.getUser(USER_ID)).isEqualTo(USER);
    }

    @Test
    void getUser_unknownId_becomesNotFound() {
        assertThatThrownBy(() -> identity.getUser(UNKNOWN_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void lookup_readsTheUsersAndKeysThemById() {
        assertThat(identity.lookup(List.of(USER_ID))).isEqualTo(Map.of(USER_ID, USER));
    }

    @Test
    void searchStudents_readsThePageContent() {
        assertThat(identity.searchStudents("perera")).containsExactly(USER);
    }
}
