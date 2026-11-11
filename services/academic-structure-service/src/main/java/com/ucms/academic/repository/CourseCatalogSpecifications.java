package com.ucms.academic.repository;

import com.ucms.academic.domain.CourseCatalog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications: combine the Course
 * Catalog screen's optional filters into one query instead of fetching everything and
 * filtering in memory. */
public final class CourseCatalogSpecifications {

    private CourseCatalogSpecifications() {}

    public static Specification<CourseCatalog> filter(UUID departmentId, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (departmentId != null) {
                predicates.add(cb.equal(root.get("owningDepartmentId"), departmentId));
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
