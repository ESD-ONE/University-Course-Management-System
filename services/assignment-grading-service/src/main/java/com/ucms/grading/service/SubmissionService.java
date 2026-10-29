package com.ucms.grading.service;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.domain.Grade;
import com.ucms.grading.domain.Submission;
import com.ucms.grading.exception.ConflictException;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.repository.GradeRepository;
import com.ucms.grading.repository.SubmissionRepository;
import com.ucms.grading.security.Caller;
import com.ucms.grading.storage.FileStorage;
import com.ucms.grading.web.dto.Dtos.MySubmissionResponse;
import com.ucms.grading.web.dto.Dtos.SubmissionRow;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private final AssignmentService assignmentService;
    private final SubmissionRepository submissions;
    private final GradeRepository grades;
    private final AcademicClient academic;
    private final EnrollmentClient enrollment;
    private final FileStorage storage;
    private final EventPublisher events;

    public SubmissionService(AssignmentService assignmentService, SubmissionRepository submissions, GradeRepository grades,
                             AcademicClient academic, EnrollmentClient enrollment, FileStorage storage, EventPublisher events) {
        this.assignmentService = assignmentService;
        this.submissions = submissions;
        this.grades = grades;
        this.academic = academic;
        this.enrollment = enrollment;
        this.storage = storage;
        this.events = events;
    }

    /** "You can replace your submission any time before the deadline" — until it's graded. */
    @Transactional
    public MySubmissionResponse submit(UUID assignmentId, MultipartFile file, Caller caller) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to submit.");
        Assignment a = assignmentService.find(assignmentId);
        if (a.getStatus() != Assignment.Status.OPEN) {
            throw new ConflictException("NOT_OPEN", "This assignment isn't accepting submissions.");
        }
        if (OffsetDateTime.now().isAfter(a.getDueDate())) {
            throw new ConflictException("PAST_DUE", "The deadline for this assignment has passed.");
        }
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        if (!enrollment.isEnrolled(caller.id(), offering.id())) {
            throw new AccessDeniedException("Not enrolled in this course");
        }
        Submission s = submissions.findByAssignmentIdAndStudentId(a.getId(), caller.id()).orElse(null);
        if (s != null && s.getStatus() == Submission.Status.GRADED) {
            throw new ConflictException("ALREADY_GRADED", "This submission has already been graded and can't be replaced.");
        }

        String name = Files.safeName(file.getOriginalFilename());
        String key = "submissions/" + a.getId() + "/" + caller.id() + "/" + UUID.randomUUID() + "-" + name;
        try {
            storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read the uploaded file.");
        }

        if (s == null) {
            s = new Submission();
            s.setAssignment(a);
            s.setStudentId(caller.id());
            s.setCreatedBy(caller.id());
        }
        s.setFileName(name);
        s.setObjectKey(key);
        s.setContentType(file.getContentType());
        s.setSizeBytes(file.getSize());
        s.setSubmittedAt(OffsetDateTime.now());
        s.setStatus(Submission.Status.SUBMITTED);
        s.setUpdatedBy(caller.id());
        s = submissions.save(s);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submissionId", s.getId().toString());
        data.put("assignmentId", a.getId().toString());
        data.put("offeringId", offering.id().toString());
        data.put("studentId", caller.id().toString());
        if (offering.lecturerId() != null) data.put("lecturerId", offering.lecturerId().toString());
        data.put("courseCode", offering.code());
        data.put("title", a.getTitle());
        events.publish("grading.submission-received", data);

        return toMine(s, a, null);
    }

    @Transactional(readOnly = true)
    public MySubmissionResponse mine(UUID assignmentId, Caller caller) {
        Assignment a = assignmentService.find(assignmentId);
        assignmentService.requireCanView(a, caller);
        Submission s = submissions.findByAssignmentIdAndStudentId(a.getId(), caller.id())
                .orElseThrow(() -> new EntityNotFoundException("You haven't submitted this assignment yet"));
        return toMine(s, a, grades.findBySubmissionId(s.getId()).orElse(null));
    }

    /** Grading screen: every enrolled student, submitted or not. */
    @Transactional(readOnly = true)
    public List<SubmissionRow> forAssignment(UUID assignmentId, Caller caller) {
        Assignment a = assignmentService.find(assignmentId);
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        AssignmentService.requireTeaches(offering, caller);

        Map<UUID, Submission> byStudent = submissions.findByAssignmentId(a.getId()).stream()
                .collect(Collectors.toMap(Submission::getStudentId, Function.identity()));
        Map<UUID, Grade> gradeBySubmission = byStudent.isEmpty() ? Map.of()
                : grades.findBySubmissionIdIn(byStudent.values().stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getSubmission().getId(), Function.identity()));

        List<SubmissionRow> rows = new ArrayList<>();
        for (EnrollmentClient.RosterEntry student : enrollment.enrolledStudents(offering.id())) {
            Submission s = byStudent.remove(student.studentId());
            rows.add(row(student.studentId(), student.studentName(), student.studentNumber(), s, a,
                    s == null ? null : gradeBySubmission.get(s.getId())));
        }
        // Submitted, then dropped the course: still shown, so the work isn't silently hidden.
        byStudent.values().forEach(s -> rows.add(row(s.getStudentId(), null, null, s, a, gradeBySubmission.get(s.getId()))));
        rows.sort(Comparator.comparing(r -> r.studentName() == null ? "~" : r.studentName()));
        return rows;
    }

    @Transactional(readOnly = true)
    public Files.Download download(UUID submissionId, Caller caller) {
        Submission s = submissions.findById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));
        boolean own = caller.isStudent() && s.getStudentId().equals(caller.id());
        if (!own) {
            AssignmentService.requireTeaches(academic.getOffering(s.getAssignment().getCourseOfferingId()), caller);
        }
        return new Files.Download(s.getFileName(), s.getContentType(), storage.get(s.getObjectKey()));
    }

    private static SubmissionRow row(UUID studentId, String name, String number, Submission s, Assignment a, Grade g) {
        if (s == null) {
            return new SubmissionRow(studentId, name, number, null, null, null, false, "NOT_SUBMITTED", null, null);
        }
        return new SubmissionRow(studentId, name, number, s.getId(), s.getFileName(), s.getSubmittedAt(),
                s.getSubmittedAt().isAfter(a.getDueDate()), s.getStatus().name(),
                g == null ? null : g.getMarks(), g == null ? null : g.getFeedback());
    }

    private static MySubmissionResponse toMine(Submission s, Assignment a, Grade g) {
        return new MySubmissionResponse(s.getId(), s.getFileName(), s.getSubmittedAt(), s.getStatus().name(),
                g == null ? null : g.getMarks(), a.getMaxMarks(), g == null ? null : g.getFeedback(),
                g == null ? null : g.getGradedAt());
    }
}
