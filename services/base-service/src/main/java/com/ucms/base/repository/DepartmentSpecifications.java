package com.ucms.base.repository;

import com.ucms.base.domain.Department;
import com.ucms.base.domain.ReferenceStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications, extended with the
 * Departments tab's existing facultyId filter. */
public final class DepartmentSpecifications {

    private DepartmentSpecifications() {}

    public static Specification<Department> filter(UUID facultyId, ReferenceStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (facultyId != null) {
                predicates.add(cb.equal(root.get("faculty").get("id"), facultyId));
            }
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
