package com.ucms.enrollment.repository;

import com.ucms.enrollment.domain.CourseEnrollment;
import com.ucms.enrollment.domain.CourseEnrollmentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications: the course roster's
 * offeringId + optional status filter, pushed to SQL instead of fetched-then-filtered. */
public final class CourseEnrollmentSpecifications {

    private CourseEnrollmentSpecifications() {}

    public static Specification<CourseEnrollment> roster(UUID offeringId, CourseEnrollmentStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("courseOfferingId"), offeringId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
