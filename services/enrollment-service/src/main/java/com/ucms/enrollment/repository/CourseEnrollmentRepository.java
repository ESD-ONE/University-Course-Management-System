package com.ucms.enrollment.repository;

import com.ucms.enrollment.domain.CourseEnrollment;
import com.ucms.enrollment.domain.CourseEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {
    Optional<CourseEnrollment> findByIdAndDeletedAtIsNull(UUID id);
    Optional<CourseEnrollment> findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(UUID studentId, UUID offeringId);
    long countByCourseOfferingIdAndStatusAndDeletedAtIsNull(UUID offeringId, CourseEnrollmentStatus status);
    List<CourseEnrollment> findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID studentId);
    List<CourseEnrollment> findByCourseOfferingIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID offeringId);
    List<CourseEnrollment> findByCourseOfferingIdInAndDeletedAtIsNull(Collection<UUID> offeringIds);
    Optional<CourseEnrollment> findFirstByCourseOfferingIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID offeringId, CourseEnrollmentStatus status);
    List<CourseEnrollment> findByDeletedAtIsNull();
    /** Includes dropped (soft-deleted) rows — used only for the enrollment-trend chart. */
    List<CourseEnrollment> findAllByOrderByCreatedAtAsc();
}
