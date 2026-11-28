package com.ucms.grading.contract;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.security.ClientCredentialsTokenProvider;
import com.ucms.grading.storage.FileStorage;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Runs the real AcademicClient against a stub of academic-structure-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:academic-structure-service",
        repositoryRoot = "stubs://file://${user.dir}/../academic-structure-service/src/test/resources/contracts/assignment-grading-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class AcademicClientContractTest {

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNKNOWN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID YEAR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID LECTURER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static final AcademicClient.Offering OFFERING = new AcademicClient.Offering(OFFERING_ID, "COSC5105",
            "Introduction to Programming", 3, PROGRAM_ID, YEAR_ID, "2025/2026", "ACTIVE", "Semester 1", 60,
            LECTURER_ID, "PUBLISHED");

    @Autowired AcademicClient academic;

    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;

    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void getOffering_readsEveryFieldTheServiceUses() {
        assertThat(academic.getOffering(OFFERING_ID)).isEqualTo(OFFERING);
    }

    @Test
    void getOffering_unknownId_becomesNotFound() {
        assertThatThrownBy(() -> academic.getOffering(UNKNOWN_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Course offering not found");
    }

    @Test
    void searchOfferings_byLecturer_readsTheListOfOfferings() {
        assertThat(academic.searchOfferings(LECTURER_ID, List.of())).containsExactly(OFFERING);
    }

    @Test
    void access_readsBothFlags() {
        assertThat(academic.access(OFFERING_ID, "caller-token")).isEqualTo(new AcademicClient.Access(true, false));
    }

    @Test
    void access_unknownOffering_becomesNotFound() {
        assertThatThrownBy(() -> academic.access(UNKNOWN_ID, "caller-token"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Course offering not found");
    }
}
