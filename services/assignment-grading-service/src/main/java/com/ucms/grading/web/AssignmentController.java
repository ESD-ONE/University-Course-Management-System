package com.ucms.grading.web;

import com.ucms.grading.domain.Assignment;
import com.ucms.grading.security.Caller;
import com.ucms.grading.service.AssignmentService;
import com.ucms.grading.service.Files;
import com.ucms.grading.service.GradingService;
import com.ucms.grading.service.SubmissionService;
import com.ucms.grading.web.dto.Dtos.AssignmentResponse;
import com.ucms.grading.web.dto.Dtos.BulkGradeRequest;
import com.ucms.grading.web.dto.Dtos.CreateAssignmentRequest;
import com.ucms.grading.web.dto.Dtos.GradeRequest;
import com.ucms.grading.web.dto.Dtos.GradeResponse;
import com.ucms.grading.web.dto.Dtos.MySubmissionResponse;
import com.ucms.grading.web.dto.Dtos.StatusRequest;
import com.ucms.grading.web.dto.Dtos.SubmissionRow;
import com.ucms.grading.web.dto.Dtos.UpdateAssignmentRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
public class AssignmentController {

    private final AssignmentService assignments;
    private final SubmissionService submissions;
    private final GradingService grading;

    public AssignmentController(AssignmentService assignments, SubmissionService submissions, GradingService grading) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.grading = grading;
    }

    @GetMapping("/assignments")
    public List<AssignmentResponse> list(@RequestParam(name = "offeringId", required = false) UUID offeringId,
                                         @RequestParam(name = "status", required = false) Assignment.Status status) {
        return assignments.list(offeringId, status, Caller.current());
    }

    @GetMapping("/assignments/{id}")
    public AssignmentResponse get(@PathVariable("id") UUID id) {
        return assignments.get(id, Caller.current());
    }

    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResponse> create(@Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignments.create(request, Caller.current()));
    }

    @PutMapping("/assignments/{id}")
    public AssignmentResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateAssignmentRequest request) {
        return assignments.update(id, request, Caller.current());
    }

    @PatchMapping("/assignments/{id}/status")
    public AssignmentResponse changeStatus(@PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return assignments.changeStatus(id, request.status(), Caller.current());
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        assignments.delete(id, Caller.current());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/assignments/{id}/brief", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssignmentResponse uploadBrief(@PathVariable("id") UUID id, @RequestPart("file") MultipartFile file) {
        return assignments.uploadBrief(id, file, Caller.current());
    }

    @GetMapping("/assignments/{id}/brief")
    public ResponseEntity<InputStreamResource> downloadBrief(@PathVariable("id") UUID id) {
        return asAttachment(assignments.downloadBrief(id, Caller.current()));
    }

    @GetMapping("/assignments/{id}/submissions")
    public List<SubmissionRow> submissions(@PathVariable("id") UUID id) {
        return submissions.forAssignment(id, Caller.current());
    }

    @PostMapping(path = "/assignments/{id}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MySubmissionResponse> submit(@PathVariable("id") UUID id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submissions.submit(id, file, Caller.current()));
    }

    @GetMapping("/assignments/{id}/submissions/me")
    public MySubmissionResponse mySubmission(@PathVariable("id") UUID id) {
        return submissions.mine(id, Caller.current());
    }

    @GetMapping("/submissions/{id}/file")
    public ResponseEntity<InputStreamResource> downloadSubmission(@PathVariable("id") UUID id) {
        return asAttachment(submissions.download(id, Caller.current()));
    }

    @PutMapping("/submissions/{id}/grade")
    public GradeResponse grade(@PathVariable("id") UUID id, @Valid @RequestBody GradeRequest request) {
        return grading.grade(id, request, Caller.current());
    }

    @PutMapping("/assignments/{id}/grades")
    public List<GradeResponse> gradeAll(@PathVariable("id") UUID id, @Valid @RequestBody BulkGradeRequest request) {
        return grading.gradeAll(id, request.grades(), Caller.current());
    }

    static ResponseEntity<InputStreamResource> asAttachment(Files.Download d) {
        MediaType type;
        try {
            type = d.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(d.contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName()).build().toString())
                .body(new InputStreamResource(d.content()));
    }
}
