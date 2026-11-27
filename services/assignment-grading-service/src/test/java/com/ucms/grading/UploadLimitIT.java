package com.ucms.grading;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.BaseClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.client.IdentityClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.repository.AssignmentRepository;
import com.ucms.grading.repository.SubmissionRepository;
import com.ucms.grading.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The 20 MB upload limit is enforced by the web server (spring.servlet.multipart), which
 * MockMvc bypasses - so this test sends real HTTP requests to a real port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadLimitIT {

    private static final int MB = 1024 * 1024;

    @Autowired TestRestTemplate http;
    @Autowired AssignmentRepository assignments;
    @Autowired SubmissionRepository submissions;
    @MockBean AcademicClient academic;
    @MockBean EnrollmentClient enrollment;
    @MockBean IdentityClient identity;
    @MockBean BaseClient base;
    @MockBean FileStorage storage;
    @MockBean EventPublisher events;

    private final UUID studentId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        submissions.deleteAll();
        assignments.deleteAll();
    }

    @Test
    void aSubmissionOver20MB_isRejectedBeforeItReachesStorage() {
        Assignment assignment = openAssignment();

        // Over the 20MB file limit, but inside the 21MB request limit.
        ResponseEntity<String> response = submit(assignment, 20 * MB + MB / 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verify(storage, never()).put(anyString(), any(), anyLong(), any());
    }

    @Test
    void aSubmissionUnderTheLimit_isAccepted() {
        Assignment assignment = openAssignment();

        ResponseEntity<String> response = submit(assignment, MB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(storage).put(anyString(), any(), anyLong(), any());
    }

    private Assignment openAssignment() {
        AcademicClient.Offering offering = new AcademicClient.Offering(UUID.randomUUID(), "COSC5105",
                "Intro to Programming", 3, UUID.randomUUID(), UUID.randomUUID(), "2025/2026", "ACTIVE", "Semester 1",
                40, UUID.randomUUID(), "PUBLISHED");
        when(academic.getOffering(offering.id())).thenReturn(offering);
        when(enrollment.isEnrolled(studentId, offering.id())).thenReturn(true);

        Assignment a = new Assignment();
        a.setCourseOfferingId(offering.id());
        a.setTitle("Programming Exercise 2");
        a.setDueDate(OffsetDateTime.now().plusDays(3));
        a.setMaxMarks(100);
        a.setStatus(Assignment.Status.OPEN);
        return assignments.save(a);
    }

    private ResponseEntity<String> submit(Assignment assignment, int sizeInBytes) {
        ByteArrayResource file = new ByteArrayResource(new byte[sizeInBytes]) {
            @Override
            public String getFilename() {
                return "submission.zip";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(KeycloakTestTokens.user(studentId, "STUDENT"));
        return http.postForEntity("/assignments/" + assignment.getId() + "/submissions",
                new HttpEntity<>(body, headers), String.class);
    }
}
