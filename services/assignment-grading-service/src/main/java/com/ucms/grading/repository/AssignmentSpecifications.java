package com.ucms.grading.repository;

import com.ucms.grading.domain.Assignment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications: the Assignments
 * screens' optional offeringId-set + status filters, pushed to SQL instead of
 * fetched-then-filtered. Which offerings a caller may see (their own courses, a
 * student's enrolled courses, or everything for an admin) is still resolved exactly as
 * before in AssignmentService — this only pushes the final fetch into one query. */
public final class AssignmentSpecifications {

    private AssignmentSpecifications() {}

    /** offeringIds == null means "no restriction" (an admin browsing everything); an
     * empty (non-null) collection means "nothing matches", not "unfiltered". */
    public static Specification<Assignment> filter(Collection<UUID> offeringIds, Assignment.Status status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (offeringIds != null) {
                predicates.add(offeringIds.isEmpty() ? cb.disjunction() : root.get("courseOfferingId").in(offeringIds));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
