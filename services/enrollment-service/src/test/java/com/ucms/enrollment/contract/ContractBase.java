package com.ucms.enrollment.contract;

import com.ucms.enrollment.KeycloakTestTokens;
import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.service.CourseEnrollmentService;
import com.ucms.enrollment.service.ProgramEnrollmentService;
import com.ucms.enrollment.service.RegistrationQueryService;
import com.ucms.enrollment.web.dto.Dtos.CourseEnrollmentResponse;
import com.ucms.enrollment.web.dto.Dtos.EnrollmentCheckResponse;
import com.ucms.enrollment.web.dto.Dtos.MonthCount;
import com.ucms.enrollment.web.dto.Dtos.OfferingCount;
import com.ucms.enrollment.web.dto.Dtos.ProgramCount;
import com.ucms.enrollment.web.dto.Dtos.ProgramEnrollmentResponse;
import com.ucms.enrollment.web.dto.Dtos.RegistrationResponse;
import com.ucms.enrollment.web.dto.Dtos.SeatCountResponse;
import com.ucms.enrollment.web.dto.Dtos.StatsResponse;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * response; the behaviour behind it is covered by EnrollmentServiceTest. Requests carry
 * a service token, as calls from other services do.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ContractBase {

    static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID YEAR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    static final UUID LECTURER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    static final UUID ENROLLMENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    static final UUID REGISTRATION_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    static final UUID ADMIN_ID = UUID.fromString("abababab-abab-abab-abab-abababababab");

    @Autowired WebApplicationContext context;
    @MockBean AcademicClient academic;
    @MockBean IdentityClient identity;
    @MockBean EventPublisher events;
    @MockBean CourseEnrollmentService courseEnrollments;
    @MockBean ProgramEnrollmentService programEnrollments;
    @MockBean RegistrationQueryService registrations;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:enrollment")))
                .build());

        CourseEnrollmentResponse enrollment = new CourseEnrollmentResponse(ENROLLMENT_ID, STUDENT_ID, "K. Perera",
                "STU-2025-0001", "k.perera@uni.edu", "BSc in Computer Science", 1, OFFERING_ID, "COSC5105",
                "Introduction to Programming", 3, "CORE", "2025/2026", "Semester 1", LECTURER_ID, "Dr. N. Silva",
                LocalDate.of(2025, 9, 2), "ENROLLED");
        when(courseEnrollments.check(STUDENT_ID, OFFERING_ID)).thenReturn(new EnrollmentCheckResponse(true, "ENROLLED"));
        when(courseEnrollments.roster(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(enrollment)));
        when(courseEnrollments.seats(any())).thenReturn(List.of(new SeatCountResponse(OFFERING_ID, 42, 3)));
        when(courseEnrollments.mine(any())).thenReturn(List.of(enrollment));

        when(programEnrollments.mine(any())).thenReturn(List.of(new ProgramEnrollmentResponse(
                UUID.fromString("12121212-1212-1212-1212-121212121212"), STUDENT_ID, "K. Perera", "STU-2025-0001",
                PROGRAM_ID, "BSC-CS", "BSc in Computer Science", 4, 120, YEAR_ID, "2025/2026", 1, "ACTIVE",
                OffsetDateTime.parse("2025-09-01T08:00:00Z"))));

        when(registrations.stats()).thenReturn(new StatsResponse(5, 42, 3,
                List.of(new ProgramCount(PROGRAM_ID, "BSc in Computer Science", 5)),
                List.of(new OfferingCount(OFFERING_ID, 42, 3)),
                List.of(new MonthCount("2025-09", 12))));
        when(registrations.recent(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new RegistrationResponse(REGISTRATION_ID, STUDENT_ID, "K. Perera", "STU-2025-0001", "COURSE", OFFERING_ID,
                        "COSC5105 Introduction to Programming", ADMIN_ID, "A. Admin", "ADMIN", "ENROLLED",
                        OffsetDateTime.parse("2025-09-02T08:30:00Z")))));
    }
}
