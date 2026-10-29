package com.ucms.analytics;

import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.domain.Report;
import com.ucms.analytics.messaging.EventPublisher;
import com.ucms.analytics.repository.ReportRepository;
import com.ucms.analytics.service.ScheduleService;
import com.ucms.analytics.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalyticsReportingTest {

    @Autowired MockMvc mockMvc;
    @Autowired ReportRepository reportRepository;
    @Autowired ScheduleService scheduleService;
    @MockBean SourceClients sources;
    @MockBean FileStorage storage;
    @MockBean EventPublisher events;

    private final UUID adminId = UUID.randomUUID();
    private final UUID lecturerId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();
    private SourceClients.Offering offering;
    private Report csvReport;
    private Report pdfReport;
    private byte[] stored;

    @BeforeEach
    void seed() {
        offering = new SourceClients.Offering(UUID.randomUUID(), "COSC5105", "Intro to Programming", 3, programId,
                "BSc Computer Science", "2025/2026", "ACTIVE", "Semester 1", 40, lecturerId, "PUBLISHED");
        when(sources.userSummary()).thenReturn(new SourceClients.UserSummary(10, 9, 7, 3));
        when(sources.offerings(any())).thenReturn(List.of(offering));
        when(sources.programs()).thenReturn(List.of(new SourceClients.Program(programId, "BSC-CS", "BSc Computer Science", 120, "ACTIVE")));
        when(sources.enrollmentStats()).thenReturn(new SourceClients.EnrollmentStats(5, 12, 2,
                List.of(new SourceClients.ProgramCount(programId, "BSc Computer Science", 5)),
                List.of(new SourceClients.OfferingCount(offering.id(), 12, 2)),
                List.of(new SourceClients.MonthCount("2026-09", 8), new SourceClients.MonthCount("2026-10", 4))));
        when(sources.registrations(anyInt())).thenReturn(List.of(new SourceClients.Registration(UUID.randomUUID(),
                "K. Perera", "STU-2025-0001", "COURSE", "=HYPERLINK(\"x\")", "R. Fernando", "ADMIN", "COURSE_ENROLL",
                OffsetDateTime.now())));
        when(sources.lookupUsers(any())).thenReturn(Map.of(lecturerId,
                new SourceClients.User(lecturerId, "Dr. N. Silva", "n@uni.edu", "LECTURER", "ACTIVE", null)));
        when(sources.offeringSummaries(any())).thenReturn(List.of(new SourceClients.OfferingSummary(offering.id(),
                "COSC5105", "Intro to Programming", 12, 2, 20, 15, 5, 78.5)));
        when(sources.courseResults()).thenReturn(List.of(
                new SourceClients.CourseResult(offering.id(), UUID.randomUUID(), 88, 2),
                new SourceClients.CourseResult(offering.id(), UUID.randomUUID(), 35, 2)));

        csvReport = report("Course Pass Rate", "CSV");
        pdfReport = report("Grade Distribution", "PDF");
        when(storage.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(stored));
        org.mockito.Mockito.doAnswer(inv -> {
            stored = ((InputStream) inv.getArgument(1)).readAllBytes();
            return null;
        }).when(storage).put(anyString(), any(), anyLong(), anyString());
    }

    private Report report(String name, String format) {
        Report r = new Report();
        r.setName(name);
        r.setDescription(name);
        r.setFormat(format);
        return reportRepository.save(r);
    }

    private String token(String role, UUID subject) {
        return "Bearer " + KeycloakTestTokens.user(subject, role);
    }

    @Test
    void adminDashboard_isComputedFromTheSourceServices() throws Exception {
        mockMvc.perform(get("/dashboards/admin").header("Authorization", token("ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalStudents").value(7))
                .andExpect(jsonPath("$.kpis.activeCourses").value(1))
                .andExpect(jsonPath("$.kpis.waitlisted").value(2))
                .andExpect(jsonPath("$.enrollmentTrend[1].value").value(12.0))
                .andExpect(jsonPath("$.topCourses[0].lecturerName").value("Dr. N. Silva"))
                .andExpect(jsonPath("$.studentsByProgram[0].label").value("BSc Computer Science"));
    }

    @Test
    void dashboards_areRoleGated() throws Exception {
        mockMvc.perform(get("/dashboards/admin").header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/reports").header("Authorization", token("STUDENT", studentId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/dashboards/student").header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerDashboard_aggregatesTheirCourses() throws Exception {
        mockMvc.perform(get("/dashboards/lecturer").header("Authorization", token("LECTURER", lecturerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myCourses").value(1))
                .andExpect(jsonPath("$.totalStudents").value(12))
                .andExpect(jsonPath("$.toGrade").value(5))
                .andExpect(jsonPath("$.averagePerformance").value(78.5))
                .andExpect(jsonPath("$.notSubmitted").value(4));
    }

    @Test
    void studentDashboard_combinesProgramEnrollmentsGradesAndDeadlines() throws Exception {
        when(sources.myPrograms(anyString())).thenReturn(List.of(new SourceClients.MyProgram(programId, "BSC-CS",
                "BSc Computer Science", 120, 4, 2, "2024/2025", "ACTIVE")));
        when(sources.myEnrollments(anyString())).thenReturn(List.of(new SourceClients.MyEnrollment(offering.id(),
                "COSC5105", "Intro to Programming", 3, "CORE", "Dr. N. Silva", "2025/2026", "Semester 1", "ENROLLED")));
        when(sources.gradeHistory(anyString())).thenReturn(new SourceClients.GradeHistory(List.of(
                new SourceClients.CourseGrade(offering.id(), "COSC5105", "Intro to Programming", 3, "2025/2026",
                        "ACTIVE", "Semester 1", 2, 88.0, "A", 4.0)), 4.0, 3));
        when(sources.myAssignments(anyString())).thenReturn(List.of(
                new SourceClients.StudentAssignment(UUID.randomUUID(), "COSC5105", "Ex 2", OffsetDateTime.now().plusDays(2), "OPEN", "NOT_SUBMITTED"),
                new SourceClients.StudentAssignment(UUID.randomUUID(), "COSC5105", "Ex 3", OffsetDateTime.now().plusDays(30), "OPEN", "NOT_SUBMITTED")));

        mockMvc.perform(get("/dashboards/student").header("Authorization", token("STUDENT", studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value("BSc Computer Science"))
                .andExpect(jsonPath("$.enrolledCourses").value(1))
                .andExpect(jsonPath("$.creditsRequired").value(120))
                .andExpect(jsonPath("$.gpa").value(4.0))
                .andExpect(jsonPath("$.upcomingDeadlines").value(1))
                .andExpect(jsonPath("$.courses[0].letter").value("A"));
    }

    @Test
    void downloadBeforeGenerate_is404_thenGenerateStoresTheFileAndDownloadReturnsIt() throws Exception {
        mockMvc.perform(get("/reports/" + csvReport.getId() + "/download").header("Authorization", token("ADMIN", adminId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/reports/" + csvReport.getId() + "/generate").header("Authorization", token("ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        String csv = mockMvc.perform(get("/reports/" + csvReport.getId() + "/download").header("Authorization", token("ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"COSC5105\"").contains("\"50.0\"");
    }

    @Test
    void pdfReport_isARealPdf() throws Exception {
        mockMvc.perform(post("/reports/" + pdfReport.getId() + "/generate").header("Authorization", token("ADMIN", adminId)))
                .andExpect(status().isOk());
        assertThat(new String(stored, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void csvCells_thatLookLikeFormulas_areNeutralized() throws Exception {
        Report registrations = report("Registration Activity", "CSV");
        mockMvc.perform(post("/reports/" + registrations.getId() + "/generate").header("Authorization", token("ADMIN", adminId)));
        assertThat(new String(stored, StandardCharsets.UTF_8)).contains("\"'=HYPERLINK(\"\"x\"\")\"");
    }

    @Test
    void schedulingAReport_computesTheNextRun_andCanBePausedAndResumed() throws Exception {
        String body = "{\"reportId\":\"" + csvReport.getId() + "\",\"frequency\":\"WEEKLY\",\"time\":\"08:00\","
                + "\"dayOfWeek\":\"MONDAY\",\"recipients\":\"r.fernando@university.edu\"}";
        String created = mockMvc.perform(post("/scheduled-reports").header("Authorization", token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.frequencyLabel").value("Weekly · Mondays 08:00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextRunAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(patch("/scheduled-reports/" + id + "/status").header("Authorization", token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void scheduling_rejectsBadRecipients_andMissingWeekday() throws Exception {
        mockMvc.perform(post("/scheduled-reports").header("Authorization", token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"" + csvReport.getId() + "\",\"frequency\":\"DAILY\",\"time\":\"06:00\",\"recipients\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/scheduled-reports").header("Authorization", token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"" + csvReport.getId() + "\",\"frequency\":\"WEEKLY\",\"time\":\"06:00\",\"recipients\":\"a@b.co\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runningASchedule_generatesTheReport_andAnnouncesItForEmailDelivery() throws Exception {
        String created = mockMvc.perform(post("/scheduled-reports").header("Authorization", token("ADMIN", adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"" + csvReport.getId() + "\",\"frequency\":\"DAILY\",\"time\":\"06:00\",\"recipients\":\"a@uni.edu, b@uni.edu\"}"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        scheduleService.runOne(id);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq("analytics.report-generated"), data.capture());
        assertThat(data.getValue()).containsEntry("reportName", "Course Pass Rate")
                .containsEntry("recipients", "a@uni.edu, b@uni.edu");
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/reports")).andExpect(status().isUnauthorized());
        verify(events, org.mockito.Mockito.never()).publish(anyString(), anyMap());
    }
}
