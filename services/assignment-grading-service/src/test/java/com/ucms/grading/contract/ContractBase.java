package com.ucms.grading.contract;

import com.ucms.grading.KeycloakTestTokens;
import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.BaseClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.client.IdentityClient;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.service.AssignmentService;
import com.ucms.grading.service.GradingService;
import com.ucms.grading.storage.FileStorage;
import com.ucms.grading.web.dto.Dtos.AssignmentResponse;
import com.ucms.grading.web.dto.Dtos.CourseGradeRow;
import com.ucms.grading.web.dto.Dtos.CourseResultRow;
import com.ucms.grading.web.dto.Dtos.GradeHistoryResponse;
import com.ucms.grading.web.dto.Dtos.OfferingSummaryResponse;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base class of the tests generated from src/test/resources/contracts (one per file).
 * Each contract is what another service expects from this one, so a generated test fails
 * when a response here stops matching what a caller reads.
 *
 * The real controllers, JSON mapping and security run. The services behind them are
 * mocked and return the fixed answers below, because a contract is about the shape of a
 * response; the behaviour behind it is covered by AssignmentGradingTest. Requests carry
 * a service token, as calls from other services do.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ContractBase {

    static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID ASSIGNMENT_ID = UUID.fromString("13131313-1313-1313-1313-131313131313");

    @Autowired WebApplicationContext context;
    @MockBean AcademicClient academic;
    @MockBean EnrollmentClient enrollment;
    @MockBean IdentityClient identity;
    @MockBean BaseClient base;
    @MockBean FileStorage storage;
    @MockBean EventPublisher events;
    @MockBean GradingService grading;
    @MockBean AssignmentService assignments;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:grading")))
                .build());

        when(grading.courseResults()).thenReturn(List.of(new CourseResultRow(OFFERING_ID, STUDENT_ID, 78.5, 2)));
        when(grading.offeringSummaries(any())).thenReturn(List.of(new OfferingSummaryResponse(OFFERING_ID, "COSC5105",
                "Introduction to Programming", 42, 3, 30, 20, 10, 71.5, ASSIGNMENT_ID)));
        when(grading.history(any())).thenReturn(new GradeHistoryResponse(List.of(new CourseGradeRow(OFFERING_ID,
                "COSC5105", "Introduction to Programming", 3, "2025/2026", "ACTIVE", "Semester 1", 2, 78.5, "B", 3.0)),
                3.0, 3));

        when(assignments.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(new AssignmentResponse(
                ASSIGNMENT_ID, OFFERING_ID, "COSC5105", "Introduction to Programming", "Essay 1", "Write an essay.",
                OffsetDateTime.parse("2025-10-01T17:00:00Z"), 100, "OPEN", null, 42, 30, 20, "NOT_SUBMITTED", null,
                null, null, null, null, null, false))));
    }
}
