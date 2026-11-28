package com.ucms.enrollment.contract;

import com.ucms.enrollment.client.AcademicClient;
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

import java.time.OffsetDateTime;
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
        repositoryRoot = "stubs://file://${user.dir}/../academic-structure-service/src/test/resources/contracts/enrollment-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class AcademicClientContractTest {

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNKNOWN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID YEAR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID LECTURER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Autowired AcademicClient academic;

    @MockBean ClientCredentialsTokenProvider serviceTokens;

    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void getOffering_readsEveryFieldTheServiceUses() {
        assertOffering(academic.getOffering(OFFERING_ID));
    }

    @Test
    void getOffering_unknownId_becomesNotFound() {
        assertThatThrownBy(() -> academic.getOffering(UNKNOWN_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Course offering not found");
    }

    @Test
    void searchOfferings_readsTheListOfOfferings() {
        List<AcademicClient.Offering> offerings = academic.searchOfferings("PUBLISHED", YEAR_ID, null, null);

        assertThat(offerings).hasSize(1);
        assertOffering(offerings.get(0));
    }

    @Test
    void getProgram_readsEveryFieldTheServiceUses() {
        assertProgram(academic.getProgram(PROGRAM_ID));
    }

    @Test
    void getProgram_unknownId_becomesNotFound() {
        assertThatThrownBy(() -> academic.getProgram(UNKNOWN_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Degree program not found");
    }

    @Test
    void listPrograms_readsThePageContent() {
        List<AcademicClient.Program> programs = academic.listPrograms();

        assertThat(programs).hasSize(1);
        assertProgram(programs.get(0));
    }

    @Test
    void listAcademicYears_readsIdNameAndStatus() {
        assertThat(academic.listAcademicYears())
                .containsExactly(new AcademicClient.AcademicYear(YEAR_ID, "2025/2026", "ACTIVE"));
    }

    private static void assertOffering(AcademicClient.Offering offering) {
        assertThat(offering).hasNoNullFieldsOrProperties();
        assertThat(offering.id()).isEqualTo(OFFERING_ID);
        assertThat(offering.code()).isEqualTo("COSC5105");
        assertThat(offering.credits()).isEqualTo(3);
        assertThat(offering.degreeProgramId()).isEqualTo(PROGRAM_ID);
        assertThat(offering.departmentId()).isEqualTo(DEPARTMENT_ID);
        assertThat(offering.yearOfStudy()).isEqualTo(1);
        assertThat(offering.academicYearId()).isEqualTo(YEAR_ID);
        assertThat(offering.academicYearStatus()).isEqualTo("ACTIVE");
        assertThat(offering.enrollmentCloseAt()).isEqualTo(OffsetDateTime.parse("2025-09-15T00:00:00Z"));
        assertThat(offering.capacity()).isEqualTo(60);
        assertThat(offering.lecturerId()).isEqualTo(LECTURER_ID);
        assertThat(offering.status()).isEqualTo("PUBLISHED");
        assertThat(offering.openForEnrollment()).isTrue();
    }

    private static void assertProgram(AcademicClient.Program program) {
        assertThat(program).isEqualTo(new AcademicClient.Program(PROGRAM_ID, "BSC-CS", "BSc in Computer Science",
                DEPARTMENT_ID, 4, 120, "ACTIVE"));
    }
}
