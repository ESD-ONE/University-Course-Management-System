package com.ucms.grading.service;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.domain.Grade;
import com.ucms.grading.domain.Submission;
import com.ucms.grading.exception.ConflictException;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.domain.CourseWeek;
import com.ucms.grading.repository.AssignmentRepository;
import com.ucms.grading.repository.CourseWeekRepository;
import com.ucms.grading.repository.AssignmentSpecifications;
import com.ucms.grading.repository.GradeRepository;
import com.ucms.grading.repository.SubmissionRepository;
import com.ucms.grading.security.Caller;
import com.ucms.grading.storage.FileStorage;
import com.ucms.grading.web.dto.Dtos.AssignmentResponse;
import com.ucms.grading.web.dto.Dtos.CreateAssignmentRequest;
import com.ucms.grading.web.dto.Dtos.UpdateAssignmentRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final GradeRepository grades;
    private final AcademicClient academic;
    private final EnrollmentClient enrollment;
    private final FileStorage storage;
    private final EventPublisher events;
    private final CourseWeekRepository weeks;

    public AssignmentService(AssignmentRepository assignments, SubmissionRepository submissions, GradeRepository grades,
                             AcademicClient academic, EnrollmentClient enrollment, FileStorage storage, EventPublisher events,
                             CourseWeekRepository weeks) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.grades = grades;
        this.academic = academic;
        this.enrollment = enrollment;
        this.storage = storage;
        this.events = events;
        this.weeks = weeks;
    }

    @Transactional
    public AssignmentResponse create(CreateAssignmentRequest request, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(request.offeringId());
        requireTeaches(offering, caller);
        Assignment a = new Assignment();
        a.setCourseOfferingId(offering.id());
        a.setTitle(request.title().trim());
        a.setDescription(request.description());
        a.setDueDate(request.dueDate());
        a.setMaxMarks(request.maxMarks());
        a.setWeekId(resolveWeekId(request.weekId(), offering.id()));
        a.setWeightPercent(request.weightPercent());
        a.setStatus(Assignment.Status.DRAFT);
        a.setCreatedBy(caller.id());
        a.setUpdatedBy(caller.id());
        return toResponse(assignments.save(a), offering, 0, 0, 0, null, null);
    }

    @Transactional
    public AssignmentResponse update(UUID id, UpdateAssignmentRequest request, Caller caller) {
        Assignment a = find(id);
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        requireTeaches(offering, caller);
        if (request.maxMarks() != a.getMaxMarks() && hasAnyGrade(a)) {
            throw new ConflictException("ALREADY_GRADED", "Max marks can't change once grading has started.");
        }
        a.setTitle(request.title().trim());
        a.setDescription(request.description());
        a.setDueDate(request.dueDate());
        a.setMaxMarks(request.maxMarks());
        a.setWeekId(resolveWeekId(request.weekId(), offering.id()));
        a.setWeightPercent(request.weightPercent());
        a.setUpdatedBy(caller.id());
        return withCounts(List.of(assignments.save(a)), Map.of(offering.id(), offering), null).get(0);
    }

    private UUID resolveWeekId(UUID weekId, UUID offeringId) {
        if (weekId == null) return null;
        CourseWeek w = weeks.findByIdAndDeletedAtIsNull(weekId)
                .orElseThrow(() -> new EntityNotFoundException("Week not found"));
        if (!w.getCourseOfferingId().equals(offeringId)) {
            throw new IllegalArgumentException("That week belongs to a different course.");
        }
        return weekId;
    }

    /** DRAFT → OPEN publishes it to enrolled students (grading.assignment-published).
     *  Back to DRAFT is only allowed while nothing has been submitted. */
    @Transactional
    public AssignmentResponse changeStatus(UUID id, Assignment.Status status, Caller caller) {
        Assignment a = find(id);
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        requireTeaches(offering, caller);
        if (status == Assignment.Status.DRAFT && submissions.existsByAssignmentId(a.getId())) {
            throw new ConflictException("HAS_SUBMISSIONS", "Students have already submitted — close it instead of un-publishing.");
        }
        boolean publishing = status == Assignment.Status.OPEN && a.getStatus() == Assignment.Status.DRAFT;
        a.setStatus(status);
        a.setUpdatedBy(caller.id());
        a = assignments.save(a);
        if (publishing) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("assignmentId", a.getId().toString());
            data.put("offeringId", offering.id().toString());
            data.put("courseCode", offering.code());
            data.put("courseName", offering.name());
            data.put("title", a.getTitle());
            data.put("dueDate", a.getDueDate().toString());
            events.publish("grading.assignment-published", data);
        }
        return withCounts(List.of(a), Map.of(offering.id(), offering), null).get(0);
    }

    /** Holding back marks/feedback from the student view while grading is in progress,
     * then releasing them together — toggled independently of DRAFT/OPEN/CLOSED status. */
    @Transactional
    public AssignmentResponse setGradesHidden(UUID id, boolean hidden, Caller caller) {
        Assignment a = find(id);
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        requireTeaches(offering, caller);
        a.setGradesHidden(hidden);
        a.setUpdatedBy(caller.id());
        a = assignments.save(a);
        return withCounts(List.of(a), Map.of(offering.id(), offering), null).get(0);
    }

    @Transactional
    public void delete(UUID id, Caller caller) {
        Assignment a = find(id);
        requireTeaches(academic.getOffering(a.getCourseOfferingId()), caller);
        if (submissions.existsByAssignmentId(a.getId())) {
            throw new ConflictException("HAS_SUBMISSIONS", "This assignment has submissions and can't be deleted — close it instead.");
        }
        a.setDeletedAt(OffsetDateTime.now());
        a.setDeletedBy(caller.id());
        a.setUpdatedBy(caller.id());
        assignments.save(a);
    }

    @Transactional
    public AssignmentResponse uploadBrief(UUID id, MultipartFile file, Caller caller) {
        Assignment a = find(id);
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        requireTeaches(offering, caller);
        String name = Files.safeName(file.getOriginalFilename());
        String key = "briefs/" + a.getId() + "/" + UUID.randomUUID() + "-" + name;
        try {
            storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read the uploaded file.");
        }
        a.setBriefFileName(name);
        a.setBriefObjectKey(key);
        a.setUpdatedBy(caller.id());
        return withCounts(List.of(assignments.save(a)), Map.of(offering.id(), offering), null).get(0);
    }

    @Transactional(readOnly = true)
    public Files.Download downloadBrief(UUID id, Caller caller) {
        Assignment a = find(id);
        requireCanView(a, caller);
        if (a.getBriefObjectKey() == null) throw new EntityNotFoundException("This assignment has no brief attached");
        return new Files.Download(a.getBriefFileName(), "application/octet-stream", storage.get(a.getBriefObjectKey()));
    }

    /**
     * ADMIN: every assignment. LECTURER: assignments in offerings they teach. STUDENT:
     * published (OPEN/CLOSED) assignments in offerings they're enrolled in, with their own
     * submission status.
     */
    @Transactional(readOnly = true)
    public Page<AssignmentResponse> list(UUID offeringId, Assignment.Status status, Pageable pageable, Caller caller) {
        Set<UUID> allowedOfferingIds;
        Map<UUID, AcademicClient.Offering> offerings;
        boolean excludeDraft = false;
        if (caller.isStudent()) {
            allowedOfferingIds = enrollment.myEnrollments(caller.token()).stream()
                    .filter(e -> "ENROLLED".equals(e.status()))
                    .map(EnrollmentClient.MyEnrollment::offeringId).collect(Collectors.toSet());
            if (offeringId != null) allowedOfferingIds.retainAll(Set.of(offeringId));
            excludeDraft = true;
            offerings = offeringsById(allowedOfferingIds);
        } else if (caller.isLecturer()) {
            offerings = academic.searchOfferings(caller.id(), null).stream()
                    .collect(Collectors.toMap(AcademicClient.Offering::id, Function.identity(), (x, y) -> x));
            allowedOfferingIds = offeringId == null ? offerings.keySet()
                    : offerings.containsKey(offeringId) ? Set.of(offeringId) : Set.of();
        } else {
            // Admin: no restriction beyond an explicit offeringId, if given.
            allowedOfferingIds = offeringId == null ? null : Set.of(offeringId);
            offerings = null; // resolved below, once we know which offerings the page actually returned
        }

        var spec = AssignmentSpecifications.filter(allowedOfferingIds, status);
        if (excludeDraft) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), Assignment.Status.DRAFT));
        }
        Page<Assignment> page = assignments.findAll(spec, pageable);
        if (page.isEmpty()) return page.map(a -> null);
        if (offerings == null) {
            offerings = offeringsById(page.getContent().stream().map(Assignment::getCourseOfferingId).collect(Collectors.toSet()));
        }
        Map<UUID, AcademicClient.Offering> resolvedOfferings = offerings;
        List<AssignmentResponse> enriched = withCounts(page.getContent(), resolvedOfferings, caller.isStudent() ? caller.id() : null);
        return new PageImpl<>(enriched, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AssignmentResponse get(UUID id, Caller caller) {
        Assignment a = find(id);
        AcademicClient.Offering offering = requireCanView(a, caller);
        return withCounts(List.of(a), Map.of(offering.id(), offering), caller.isStudent() ? caller.id() : null).get(0);
    }

    // ---- shared helpers (also used by SubmissionService / GradingService) ----

    Assignment find(UUID id) {
        return assignments.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    }

    static void requireTeaches(AcademicClient.Offering offering, Caller caller) {
        if (caller.isAdmin()) return;
        if (caller.isLecturer() && caller.id().equals(offering.lecturerId())) return;
        throw new AccessDeniedException("You don't teach this course");
    }

    /** Staff who teach it, admins, or a student enrolled in it (published only). */
    AcademicClient.Offering requireCanView(Assignment a, Caller caller) {
        AcademicClient.Offering offering = academic.getOffering(a.getCourseOfferingId());
        if (caller.isStudent()) {
            if (a.getStatus() == Assignment.Status.DRAFT || !enrollment.isEnrolled(caller.id(), offering.id())) {
                throw new AccessDeniedException("Not enrolled in this course");
            }
            return offering;
        }
        requireTeaches(offering, caller);
        return offering;
    }

    private boolean hasAnyGrade(Assignment a) {
        List<UUID> subIds = submissions.findByAssignmentId(a.getId()).stream().map(Submission::getId).toList();
        return !subIds.isEmpty() && !grades.findBySubmissionIdIn(subIds).isEmpty();
    }

    private Map<UUID, AcademicClient.Offering> offeringsById(Collection<UUID> ids) {
        return academic.searchOfferings(null, ids).stream()
                .collect(Collectors.toMap(AcademicClient.Offering::id, Function.identity(), (x, y) -> x));
    }

    private List<AssignmentResponse> withCounts(List<Assignment> rows, Map<UUID, AcademicClient.Offering> offerings, UUID studentId) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, Long> enrolledCounts = enrollment.enrolledCounts(
                rows.stream().map(Assignment::getCourseOfferingId).collect(Collectors.toSet()));
        List<Submission> subs = submissions.findByAssignmentIdIn(rows.stream().map(Assignment::getId).toList());
        Map<UUID, Grade> gradeBySubmission = subs.isEmpty() ? Map.of()
                : grades.findBySubmissionIdIn(subs.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getSubmission().getId(), Function.identity()));
        Map<UUID, List<Submission>> byAssignment = subs.stream()
                .collect(Collectors.groupingBy(s -> s.getAssignment().getId()));
        return rows.stream().map(a -> {
            List<Submission> forA = byAssignment.getOrDefault(a.getId(), List.of());
            long graded = forA.stream().filter(s -> gradeBySubmission.containsKey(s.getId())).count();
            Submission mine = studentId == null ? null
                    : forA.stream().filter(s -> s.getStudentId().equals(studentId)).findFirst().orElse(null);
            Grade myGrade = mine == null ? null : gradeBySubmission.get(mine.getId());
            AcademicClient.Offering o = offerings.get(a.getCourseOfferingId());
            return toResponse(a, o, enrolledCounts.getOrDefault(a.getCourseOfferingId(), 0L), forA.size(), graded, mine, myGrade);
        }).toList();
    }

    private static AssignmentResponse toResponse(Assignment a, AcademicClient.Offering o, long total, long submitted,
                                                 long graded, Submission mine, Grade myGrade) {
        String myStatus = mine == null ? "NOT_SUBMITTED" : mine.getStatus().name();
        // Grades held back from the student view while grades_hidden is set — the
        // submission/status is still visible, only the mark and feedback are withheld.
        boolean withhold = a.isGradesHidden() && mine != null;
        return new AssignmentResponse(a.getId(), a.getCourseOfferingId(), o == null ? null : o.code(), o == null ? null : o.name(),
                a.getTitle(), a.getDescription(), a.getDueDate(), a.getMaxMarks(), a.getStatus().name(), a.getBriefFileName(),
                total, submitted, graded, myStatus, mine == null ? null : mine.getSubmittedAt(),
                mine == null ? null : mine.getFileName(), withhold ? null : (myGrade == null ? null : myGrade.getMarks()),
                withhold ? null : (myGrade == null ? null : myGrade.getFeedback()), a.getWeekId(), a.getWeightPercent(),
                a.isGradesHidden());
    }
}
