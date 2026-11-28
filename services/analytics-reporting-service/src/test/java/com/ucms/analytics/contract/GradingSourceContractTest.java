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
 * Runs the real SourceClients against a stub of assignment-grading-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:assignment-grading-service",
        repositoryRoot = "stubs://file://${user.dir}/../assignment-grading-service/src/test/resources/contracts/analytics-reporting-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class GradingSourceContractTest {

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("13131313-1313-1313-1313-131313131313");

    @Autowired SourceClients sources;
    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean FileStorage storage;
    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void courseResults_readsEachStudentsResultPerCourse() {
        assertThat(sources.courseResults()).containsExactly(new SourceClients.CourseResult(OFFERING_ID, STUDENT_ID, 78.5, 2));
    }

    @Test
    void offeringSummaries_readsTheGradingProgressOfAnOffering() {
        assertThat(sources.offeringSummaries(List.of(OFFERING_ID))).containsExactly(new SourceClients.OfferingSummary(
                OFFERING_ID, "COSC5105", "Introduction to Programming", 42, 3, 30, 20, 10, 71.5, ASSIGNMENT_ID));
    }

    @Test
    void gradeHistory_readsTheCallersGradesAndGpa() {
        assertThat(sources.gradeHistory("user-token")).isEqualTo(new SourceClients.GradeHistory(
                List.of(new SourceClients.CourseGrade(OFFERING_ID, "COSC5105", "Introduction to Programming", 3,
                        "2025/2026", "ACTIVE", "Semester 1", 2, 78.5, "B", 3.0)),
                3.0, 3));
    }

    @Test
    void myAssignments_readsThePageContent() {
        assertThat(sources.myAssignments("user-token")).containsExactly(new SourceClients.StudentAssignment(
                ASSIGNMENT_ID, "COSC5105", "Essay 1", OffsetDateTime.parse("2025-10-01T17:00:00Z"), "OPEN",
                "NOT_SUBMITTED"));
    }
}
