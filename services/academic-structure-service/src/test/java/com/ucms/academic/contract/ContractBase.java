package com.ucms.academic.contract;

import com.ucms.academic.KeycloakTestTokens;
import com.ucms.academic.security.AssignmentCache;
import com.ucms.academic.security.RoleCatalog;
import com.ucms.academic.service.AcademicCalendarService;
import com.ucms.academic.service.CourseCatalogService;
import com.ucms.academic.service.CourseOfferingQueryService;
import com.ucms.academic.service.DegreeProgramService;
import com.ucms.academic.web.dto.AcademicYearResponse;
import com.ucms.academic.web.dto.CourseCatalogResponse;
import com.ucms.academic.web.dto.CourseOfferingResponse;
import com.ucms.academic.web.dto.DegreeProgramResponse;
import com.ucms.academic.web.dto.OfferingAccessResponse;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import jakarta.persistence.EntityNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
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
 * response; the behaviour behind it is covered by AcademicStructureTest. Requests carry a
 * service token, as calls from other services do.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ContractBase {

    static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID UNKNOWN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    static final UUID YEAR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired WebApplicationContext context;
    @MockBean RoleCatalog roleCatalog;
    @MockBean AssignmentCache assignmentCache;
    @MockBean CourseOfferingQueryService offerings;
    @MockBean DegreeProgramService programs;
    @MockBean AcademicCalendarService calendar;
    @MockBean CourseCatalogService catalog;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:academic")))
                .build());

        when(offerings.get(OFFERING_ID)).thenReturn(offering());
        when(offerings.get(UNKNOWN_ID)).thenThrow(new EntityNotFoundException("Course offering not found"));
        when(offerings.search(any(), any(), any(), any(), any())).thenReturn(List.of(offering()));
        when(offerings.access(eq(OFFERING_ID), any())).thenReturn(new OfferingAccessResponse(true, false));
        when(offerings.access(eq(UNKNOWN_ID), any())).thenThrow(new EntityNotFoundException("Course offering not found"));

        DegreeProgramResponse program = new DegreeProgramResponse(PROGRAM_ID, "BSC-CS", "BSc in Computer Science",
                DEPARTMENT_ID, 4, 120, "ACTIVE");
        when(programs.get(PROGRAM_ID)).thenReturn(program);
        when(programs.get(UNKNOWN_ID)).thenThrow(new EntityNotFoundException("Degree program not found"));
        when(programs.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(program)));

        when(calendar.listYears()).thenReturn(List.of(new AcademicYearResponse(YEAR_ID, "2025/2026",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), "ACTIVE", List.of())));

        when(catalog.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(new CourseCatalogResponse(
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "COSC5105", "Introduction to Programming",
                3, DEPARTMENT_ID))));
    }

    private static CourseOfferingResponse offering() {
        return new CourseOfferingResponse(OFFERING_ID,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "COSC5105", "Introduction to Programming", 3, "CORE",
                PROGRAM_ID, "BSC-CS", "BSc in Computer Science", DEPARTMENT_ID, 1, YEAR_ID, "2025/2026", "ACTIVE",
                UUID.fromString("77777777-7777-7777-7777-777777777777"), "Semester 1",
                OffsetDateTime.parse("2025-08-01T00:00:00Z"), OffsetDateTime.parse("2025-09-15T00:00:00Z"),
                60, UUID.fromString("88888888-8888-8888-8888-888888888888"), "PUBLISHED", true);
    }
}
