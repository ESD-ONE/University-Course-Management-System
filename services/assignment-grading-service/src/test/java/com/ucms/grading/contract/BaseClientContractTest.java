package com.ucms.grading.contract;

import com.ucms.grading.client.BaseClient;
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



import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real BaseClient against a stub of base-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:base-service",
        repositoryRoot = "stubs://file://${user.dir}/../base-service/src/test/resources/contracts/assignment-grading-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class BaseClientContractTest {


    @Autowired BaseClient base;
    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;
    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void gradingScales_readsThePageContent() {
        assertThat(base.gradingScales()).containsExactly(new BaseClient.GradingScale("Standard", "A:80,B:65,C:50,F:0", "ACTIVE"));
    }
}
