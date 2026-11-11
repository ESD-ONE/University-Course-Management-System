package com.ucms.academic.repository;

import com.ucms.academic.domain.DegreeProgram;
import com.ucms.academic.domain.ProgramStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications: combine Reference
 * Degree Details' optional filters (department, status, search box) into one query
 * instead of fetching everything and filtering in memory. */
public final class DegreeProgramSpecifications {

    private DegreeProgramSpecifications() {}

    public static Specification<DegreeProgram> filter(UUID departmentId, ProgramStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
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

    /** Restricts to a caller's department-scoped View grants (a Department Head sees only
     * their own departments' programs) — an empty set means "none", not "unfiltered". */
    public static Specification<DegreeProgram> departmentIn(Collection<UUID> departmentIds) {
        return (root, query, cb) -> departmentIds.isEmpty() ? cb.disjunction() : root.get("departmentId").in(departmentIds);
    }
}
