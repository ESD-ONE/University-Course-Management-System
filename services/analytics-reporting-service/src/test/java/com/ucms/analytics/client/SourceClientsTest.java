package com.ucms.analytics.client;

import com.ucms.analytics.security.JwtValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The real SourceClients against canned HTTP responses in the shape enrollment-service
 * sends. Every other test replaces SourceClients with a mock, so nothing else checks how
 * it reads a response.
 */
class SourceClientsTest {

    private static final String ENROLLMENT = "http://enrollment-service";

    private MockRestServiceServer server;
    private SourceClients sources;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        JwtValidator jwt = mock(JwtValidator.class);
        when(jwt.mintServiceToken()).thenReturn("service-token");
        sources = new SourceClients(builder, jwt, "http://identity-access-service", "http://academic-structure-service",
                ENROLLMENT, "http://assignment-grading-service");
    }

    @Test
    void registrations_readsThePagedResponse_andAsksForTheLimitAsThePageSize() {
        server.expect(requestTo(ENROLLMENT + "/registrations?page=0&size=5"))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess(page(1, registration("11111111-1111-1111-1111-111111111111", "K. Perera")),
                        MediaType.APPLICATION_JSON));

        List<SourceClients.Registration> rows = sources.registrations(5);

        assertThat(rows).containsExactly(new SourceClients.Registration(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "K. Perera", "STU-2025-0001", "COURSE",
                "COSC5105 Introduction to Programming", "A. Admin", "ADMIN", "COURSE_ENROLL",
                OffsetDateTime.parse("2025-09-02T08:30:00Z")));
        server.verify();
    }

    @Test
    void registrations_overOnePage_readsTheNextPageToo() {
        server.expect(requestTo(ENROLLMENT + "/registrations?page=0&size=100"))
                .andRespond(withSuccess(page(2, registration("11111111-1111-1111-1111-111111111111", "K. Perera")),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENROLLMENT + "/registrations?page=1&size=100"))
                .andRespond(withSuccess(page(2, registration("22222222-2222-2222-2222-222222222222", "A. Jayawardena")),
                        MediaType.APPLICATION_JSON));

        List<SourceClients.Registration> rows = sources.registrations(200);

        assertThat(rows).extracting(SourceClients.Registration::studentName).containsExactly("K. Perera", "A. Jayawardena");
        server.verify();
    }

    @Test
    void registrations_neverReturnsMoreThanTheLimit() {
        server.expect(requestTo(ENROLLMENT + "/registrations?page=0&size=1"))
                .andRespond(withSuccess(page(3, registration("11111111-1111-1111-1111-111111111111", "K. Perera")),
                        MediaType.APPLICATION_JSON));

        assertThat(sources.registrations(1)).hasSize(1);
        server.verify();
    }

    @Test
    void registrations_whenThereAreNone_isEmpty() {
        server.expect(requestTo(ENROLLMENT + "/registrations?page=0&size=5"))
                .andRespond(withSuccess(page(0), MediaType.APPLICATION_JSON));

        assertThat(sources.registrations(5)).isEmpty();
    }

    /** The paged JSON enrollment-service's GET /registrations returns (fields this client reads, plus extras). */
    private static String page(int totalPages, String... rows) {
        return "{\"content\":[" + String.join(",", rows) + "],\"totalPages\":" + totalPages
                + ",\"totalElements\":" + rows.length + ",\"size\":20,\"number\":0}";
    }

    private static String registration(String id, String studentName) {
        return "{\"id\":\"" + id + "\",\"studentId\":\"" + UUID.randomUUID() + "\",\"studentName\":\"" + studentName
                + "\",\"studentNumber\":\"STU-2025-0001\",\"targetType\":\"COURSE\",\"targetId\":\"" + UUID.randomUUID()
                + "\",\"targetLabel\":\"COSC5105 Introduction to Programming\",\"actorId\":\"" + UUID.randomUUID()
                + "\",\"actorName\":\"A. Admin\",\"actorRole\":\"ADMIN\",\"action\":\"COURSE_ENROLL\","
                + "\"createdAt\":\"2025-09-02T08:30:00Z\"}";
    }
}
