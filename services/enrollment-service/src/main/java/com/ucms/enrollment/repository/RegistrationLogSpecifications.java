package com.ucms.enrollment.repository;

import com.ucms.enrollment.domain.RegistrationLog;
import com.ucms.enrollment.domain.TargetType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications: combine Recent
 * Registrations' optional filters into one query instead of fetching everything and
 * filtering in memory. Caller-scoping (admin sees all, a lecturer sees only what they
 * did) is folded in here too, since it's just another predicate on the same entity. */
public final class RegistrationLogSpecifications {

    private RegistrationLogSpecifications() {}

    public static Specification<RegistrationLog> filter(UUID studentId, UUID actorIdIfNotAdmin,
                                                          TargetType targetType, String action) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            } else if (actorIdIfNotAdmin != null) {
                predicates.add(cb.equal(root.get("actorId"), actorIdIfNotAdmin));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
