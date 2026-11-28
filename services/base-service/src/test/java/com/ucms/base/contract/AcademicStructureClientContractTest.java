package com.ucms.base.contract;

import com.ucms.base.client.AcademicStructureClient;
import com.ucms.base.security.ClientCredentialsTokenProvider;
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
 * Runs the real AcademicStructureClient against a stub of academic-structure-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:academic-structure-service",
        repositoryRoot = "stubs://file://${user.dir}/../academic-structure-service/src/test/resources/contracts/base-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class AcademicStructureClientContractTest {

    private static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired AcademicStructureClient academic;

    @MockBean ClientCredentialsTokenProvider serviceTokens;

    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void hasDependents_readsTheTotalFromThePagedResponse() {
        assertThat(academic.hasDependents(DEPARTMENT_ID)).isTrue();
    }
}
