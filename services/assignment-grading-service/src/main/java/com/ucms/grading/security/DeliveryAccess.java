package com.ucms.grading.security;

import com.ucms.grading.client.AcademicClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may view or manage a course offering's delivery (content, lectures, assignments,
 * grading, analytics) beyond its owning lecturer: admins always can; everyone else is
 * checked against academic-structure-service's own resolution of Program/Department
 * Head scope (see CourseOfferingQueryService#access there) — this service holds no
 * scope-assignment data of its own.
 */
@Component
public class DeliveryAccess {

    private final AcademicClient academic;

    public DeliveryAccess(AcademicClient academic) {
        this.academic = academic;
    }

    public void requireManage(AcademicClient.Offering offering, Caller caller) {
        if (caller.isAdmin()) return;
        if (caller.isLecturer() && caller.id().equals(offering.lecturerId())) return;
        if (caller.isLecturer() && academic.access(offering.id(), caller.token()).manage()) return;
        throw new AccessDeniedException("You don't manage this course's delivery");
    }

    public boolean canManage(AcademicClient.Offering offering, Caller caller) {
        if (caller.isAdmin()) return true;
        if (caller.isLecturer() && caller.id().equals(offering.lecturerId())) return true;
        return caller.isLecturer() && academic.access(offering.id(), caller.token()).manage();
    }

    public void requireStaffView(AcademicClient.Offering offering, Caller caller) {
        if (caller.isAdmin()) return;
        if (caller.isLecturer() && caller.id().equals(offering.lecturerId())) return;
        if (caller.isLecturer() && academic.access(offering.id(), caller.token()).staffView()) return;
        throw new AccessDeniedException("You don't have access to this course's delivery");
    }
}
