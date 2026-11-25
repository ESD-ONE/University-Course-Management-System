package com.ucms.grading.service;

import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.domain.LectureSession;
import com.ucms.grading.repository.AssignmentRepository;
import com.ucms.grading.repository.LectureSessionRepository;
import com.ucms.grading.security.Caller;
import com.ucms.grading.web.dto.Dtos.UpcomingItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Merges the two things a role-aware "what's coming up" dashboard widget needs —
 * published lecture sessions starting soon, and assignments due soon — from the two
 * domains that already own them here. Scoped the same way AssignmentService#list scopes
 * assignments: ADMIN sees everything, LECTURER sees offerings they teach, STUDENT sees
 * offerings they're enrolled in. See
 * docs/ASSIGNMENT_WEEK_LINKING_AND_DASHBOARD_ARCHITECTURE.md section 3.2.
 */
@Service
public class UpcomingService {

    private final AssignmentRepository assignments;
    private final LectureSessionRepository sessions;
    private final AcademicClient academic;
    private final EnrollmentClient enrollment;

    public UpcomingService(AssignmentRepository assignments, LectureSessionRepository sessions,
                           AcademicClient academic, EnrollmentClient enrollment) {
        this.assignments = assignments;
        this.sessions = sessions;
        this.academic = academic;
        this.enrollment = enrollment;
    }

    @Transactional(readOnly = true)
    public List<UpcomingItem> upcoming(int days, Caller caller) {
        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime to = from.plusDays(days);
        boolean student = caller.isStudent();

        Set<UUID> offeringIds;
        if (student) {
            offeringIds = enrollment.myEnrollments(caller.token()).stream()
                    .filter(e -> "ENROLLED".equals(e.status()))
                    .map(EnrollmentClient.MyEnrollment::offeringId).collect(Collectors.toSet());
        } else if (caller.isLecturer()) {
            offeringIds = academic.searchOfferings(caller.id(), null).stream()
                    .map(AcademicClient.Offering::id).collect(Collectors.toSet());
        } else {
            offeringIds = null; // admin: unscoped, every offering with something upcoming
        }
        if (offeringIds != null && offeringIds.isEmpty()) return List.of();

        List<Assignment> dueSoon = (offeringIds == null
                ? assignments.findByDeletedAtIsNullAndDueDateBetween(from, to)
                : assignments.findByCourseOfferingIdInAndDeletedAtIsNullAndDueDateBetween(offeringIds, from, to))
                .stream()
                .filter(a -> !student || a.getStatus() != Assignment.Status.DRAFT)
                .toList();

        List<LectureSession> startingSoon = (offeringIds == null
                ? sessions.findByDeletedAtIsNullAndStartsAtBetween(from, to)
                : sessions.findByCourseOfferingIdInAndDeletedAtIsNullAndStartsAtBetween(offeringIds, from, to))
                .stream()
                .filter(s -> !student || s.getStatus() == LectureSession.Status.PUBLISHED)
                .toList();

        if (dueSoon.isEmpty() && startingSoon.isEmpty()) return List.of();

        Set<UUID> involvedOfferings = new HashSet<>();
        dueSoon.forEach(a -> involvedOfferings.add(a.getCourseOfferingId()));
        startingSoon.forEach(s -> involvedOfferings.add(s.getCourseOfferingId()));
        Map<UUID, AcademicClient.Offering> offeringById = academic.searchOfferings(null, involvedOfferings).stream()
                .collect(Collectors.toMap(AcademicClient.Offering::id, Function.identity(), (x, y) -> x));

        List<UpcomingItem> items = new ArrayList<>();
        for (Assignment a : dueSoon) {
            AcademicClient.Offering o = offeringById.get(a.getCourseOfferingId());
            items.add(new UpcomingItem("ASSIGNMENT_DUE", a.getCourseOfferingId(), o == null ? null : o.code(),
                    o == null ? null : o.name(), a.getTitle(), a.getDueDate(), a.getId(), null, null));
        }
        for (LectureSession s : startingSoon) {
            AcademicClient.Offering o = offeringById.get(s.getCourseOfferingId());
            items.add(new UpcomingItem("LECTURE", s.getCourseOfferingId(), o == null ? null : o.code(),
                    o == null ? null : o.name(), s.getTitle(), s.getStartsAt(), null, s.getId(), s.getMeetingUrl()));
        }
        items.sort(Comparator.comparing(UpcomingItem::at));
        return items;
    }
}
