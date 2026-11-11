package com.ucms.base.repository;

import com.ucms.base.domain.Faculty;
import com.ucms.base.domain.ReferenceStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Same pattern as identity-access-service's UserSpecifications: combine the Faculties
 * tab's optional filters (status, search box) into one query instead of fetching
 * everything and filtering in memory. */
public final class FacultySpecifications {

    private FacultySpecifications() {}

    public static Specification<Faculty> filter(ReferenceStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
