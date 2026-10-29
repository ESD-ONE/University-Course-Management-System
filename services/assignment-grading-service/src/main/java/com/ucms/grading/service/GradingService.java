package com.ucms.grading.service;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.domain.Grade;
import com.ucms.grading.domain.Submission;
import com.ucms.grading.exception.ConflictException;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.repository.AssignmentRepository;
import com.ucms.grading.repository.GradeRepository;
import com.ucms.grading.repository.SubmissionRepository;
import com.ucms.grading.security.Caller;
import com.ucms.grading.web.dto.Dtos.BulkGradeItem;
import com.ucms.grading.web.dto.Dtos.CourseGradeRow;
import com.ucms.grading.web.dto.Dtos.CourseResultRow;
import com.ucms.grading.web.dto.Dtos.GradeHistoryResponse;
import com.ucms.grading.web.dto.Dtos.GradeRequest;
import com.ucms.grading.web.dto.Dtos.GradeResponse;
import com.ucms.grading.web.dto.Dtos.OfferingSummaryResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * flow-02-grading.mmd: confirm the student is enrolled (Enrollment), save the grade,
 * mark the submission GRADED, publish grading.grade-posted — Notification tells the
 * student, Analytics recomputes the class average, independently.
 *
 * A student's course percentage = total marks earned / total max marks, over that
 * course's graded assignments; the letter and GPA points come from LetterGrades.
 */
@Service
public class GradingService {

    private final SubmissionRepository submissions;
    private final GradeRepository grades;
    private final AssignmentRepository assignments;
    private final AssignmentService assignmentService;
    private final AcademicClient academic;
    private final EnrollmentClient enrollment;
    private final LetterGrades letters;
    private final EventPublisher events;

    public GradingService(SubmissionRepository submissions, GradeRepository grades, AssignmentRepository assignments,
                          AssignmentService assignmentService, AcademicClient academic, EnrollmentClient enrollment,
                          LetterGrades letters, EventPublisher events) {
        this.submissions = submissions;
        this.grades = grades;
        this.assignments = assignments;
        this.assignmentService = assignmentService;
        this.academic = academic;
        this.enrollment = enrollment;
        this.letters = letters;
        this.events = events;
    }

    @Transactional
    public GradeResponse grade(UUID submissionId, GradeRequest request, Caller caller) {
        Submission s = submissions.findById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));
        AcademicClient.Offering offering = academic.getOffering(s.getAssignment().getCourseOfferingId());
        AssignmentService.requireTeaches(offering, caller);
        return applyGrade(s, offering, request.marks(), request.feedback(), caller);
    }

    /** "Save All Grades" — all-or-nothing in one transaction. */
    @Transactional
    public List<GradeResponse> gradeAll(UUID assignmentId, List<BulkGradeItem> items, Caller caller) {
        Assignment a = assignmentService.find(assignmentId);
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        AssignmentService.requireTeaches(offering, caller);
        List<GradeResponse> result = new ArrayList<>();
        for (BulkGradeItem item : items) {
            Submission s = submissions.findById(item.submissionId())
                    .orElseThrow(() -> new EntityNotFoundException("Submission not found"));
            if (!s.getAssignment().getId().equals(a.getId())) {
                throw new IllegalArgumentException("Submission " + item.submissionId() + " belongs to a different assignment.");
            }
            result.add(applyGrade(s, offering, item.marks(), item.feedback(), caller));
        }
        return result;
    }

    private GradeResponse applyGrade(Submission s, AcademicClient.Offering offering, BigDecimal marks, String feedback, Caller caller) {
        Assignment a = s.getAssignment();
        if (marks.compareTo(BigDecimal.valueOf(a.getMaxMarks())) > 0) {
            throw new IllegalArgumentException("Marks can't exceed " + a.getMaxMarks() + ".");
        }
        if (!enrollment.isEnrolled(s.getStudentId(), offering.id())) {
            throw new ConflictException("NOT_ENROLLED", "This student is no longer enrolled in " + offering.code() + ".");
        }
        Grade g = grades.findBySubmissionId(s.getId()).orElseGet(() -> {
            Grade fresh = new Grade();
            fresh.setSubmission(s);
            fresh.setCreatedBy(caller.id());
            return fresh;
        });
        g.setMarks(marks);
        g.setFeedback(feedback);
        g.setGradedBy(caller.id());
        g.setGradedAt(OffsetDateTime.now());
        g.setUpdatedBy(caller.id());
        g = grades.save(g);
        s.setStatus(Submission.Status.GRADED);
        s.setUpdatedBy(caller.id());
        submissions.save(s);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gradeId", g.getId().toString());
        data.put("submissionId", s.getId().toString());
        data.put("assignmentId", a.getId().toString());
        data.put("offeringId", offering.id().toString());
        data.put("studentId", s.getStudentId().toString());
        data.put("courseCode", offering.code());
        data.put("title", a.getTitle());
        data.put("marks", marks.toPlainString());
        data.put("maxMarks", a.getMaxMarks());
        events.publish("grading.grade-posted", data);

        return new GradeResponse(g.getId(), s.getId(), g.getMarks(), a.getMaxMarks(), g.getFeedback(), g.getGradedBy(), g.getGradedAt());
    }

    /** Student "Grades" screen: one row per course they have graded work in, plus GPA. */
    @Transactional(readOnly = true)
    public GradeHistoryResponse history(Caller caller) {
        List<Submission> mine = submissions.findByStudentId(caller.id()).stream()
                .filter(s -> s.getAssignment().getDeletedAt() == null).toList();
        Map<UUID, Grade> gradeBySubmission = mine.isEmpty() ? Map.of()
                : grades.findBySubmissionIdIn(mine.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getSubmission().getId(), Function.identity()));
        Map<UUID, double[]> perOffering = new LinkedHashMap<>();
        for (Submission s : mine) {
            Grade g = gradeBySubmission.get(s.getId());
            if (g == null) continue;
            double[] acc = perOffering.computeIfAbsent(s.getAssignment().getCourseOfferingId(), k -> new double[3]);
            acc[0] += g.getMarks().doubleValue();
            acc[1] += s.getAssignment().getMaxMarks();
            acc[2] += 1;
        }
        if (perOffering.isEmpty()) return new GradeHistoryResponse(List.of(), null, 0);

        Map<UUID, AcademicClient.Offering> offerings = offeringsById(perOffering.keySet());
        List<CourseGradeRow> rows = new ArrayList<>();
        double weightedPoints = 0;
        int credits = 0;
        for (var e : perOffering.entrySet()) {
            AcademicClient.Offering o = offerings.get(e.getKey());
            double pct = Math.round(e.getValue()[0] / e.getValue()[1] * 1000.0) / 10.0;
            LetterGrades.Result r = letters.of(pct);
            int c = o == null ? 0 : o.credits();
            rows.add(new CourseGradeRow(e.getKey(), o == null ? null : o.code(), o == null ? null : o.name(), c,
                    o == null ? null : o.academicYearName(), o == null ? null : o.academicYearStatus(),
                    o == null ? null : o.semesterName(), (int) e.getValue()[2], pct, r.letter(), r.points()));
            weightedPoints += r.points() * c;
            credits += c;
        }
        Double gpa = credits == 0 ? null : Math.round(weightedPoints / credits * 100.0) / 100.0;
        return new GradeHistoryResponse(rows, gpa, credits);
    }

    @Transactional(readOnly = true)
    public OfferingSummaryResponse offeringSummary(UUID offeringId, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(offeringId);
        AssignmentService.requireTeaches(offering, caller);
        return summaries(Map.of(offering.id(), offering)).get(0);
    }

    /** For Analytics (service token): summaries of many offerings at once. */
    @Transactional(readOnly = true)
    public List<OfferingSummaryResponse> offeringSummaries(Collection<UUID> offeringIds) {
        if (offeringIds.isEmpty()) return List.of();
        return summaries(offeringsById(offeringIds));
    }

    private List<OfferingSummaryResponse> summaries(Map<UUID, AcademicClient.Offering> offerings) {
        Map<UUID, Long> enrolled = enrollment.enrolledCounts(offerings.keySet());
        List<Assignment> all = assignments.findByCourseOfferingIdInAndDeletedAtIsNullOrderByDueDateDesc(offerings.keySet());
        List<Submission> subs = all.isEmpty() ? List.of() : submissions.findByAssignmentIdIn(all.stream().map(Assignment::getId).toList());
        Map<UUID, Grade> gradeBySubmission = subs.isEmpty() ? Map.of()
                : grades.findBySubmissionIdIn(subs.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getSubmission().getId(), Function.identity()));
        List<OfferingSummaryResponse> result = new ArrayList<>();
        for (AcademicClient.Offering o : offerings.values()) {
            Set<UUID> assignmentIds = all.stream().filter(a -> a.getCourseOfferingId().equals(o.id()))
                    .map(Assignment::getId).collect(Collectors.toSet());
            List<Submission> forO = subs.stream().filter(s -> assignmentIds.contains(s.getAssignment().getId())).toList();
            long graded = forO.stream().filter(s -> gradeBySubmission.containsKey(s.getId())).count();
            double earned = 0, possible = 0;
            for (Submission s : forO) {
                Grade g = gradeBySubmission.get(s.getId());
                if (g == null) continue;
                earned += g.getMarks().doubleValue();
                possible += s.getAssignment().getMaxMarks();
            }
            Double average = possible == 0 ? null : Math.round(earned / possible * 1000.0) / 10.0;
            result.add(new OfferingSummaryResponse(o.id(), o.code(), o.name(), enrolled.getOrDefault(o.id(), 0L),
                    assignmentIds.size(), forO.size(), graded, forO.size() - graded, average));
        }
        return result;
    }

    /** For Analytics: each (offering, student) course percentage over graded work. */
    @Transactional(readOnly = true)
    public List<CourseResultRow> courseResults() {
        List<Submission> all = submissions.findAllLive();
        Map<UUID, Grade> gradeBySubmission = all.isEmpty() ? Map.of()
                : grades.findBySubmissionIdIn(all.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getSubmission().getId(), Function.identity()));
        Map<String, double[]> acc = new HashMap<>();
        for (Submission s : all) {
            Grade g = gradeBySubmission.get(s.getId());
            if (g == null) continue;
            double[] a = acc.computeIfAbsent(s.getAssignment().getCourseOfferingId() + "|" + s.getStudentId(), k -> new double[3]);
            a[0] += g.getMarks().doubleValue();
            a[1] += s.getAssignment().getMaxMarks();
            a[2] += 1;
        }
        return acc.entrySet().stream().map(e -> {
            String[] k = e.getKey().split("\\|");
            double pct = Math.round(e.getValue()[0] / e.getValue()[1] * 1000.0) / 10.0;
            return new CourseResultRow(UUID.fromString(k[0]), UUID.fromString(k[1]), pct, (int) e.getValue()[2]);
        }).toList();
    }

    private Map<UUID, AcademicClient.Offering> offeringsById(Collection<UUID> ids) {
        return academic.searchOfferings(null, ids).stream()
                .collect(Collectors.toMap(AcademicClient.Offering::id, Function.identity(), (x, y) -> x));
    }
}
