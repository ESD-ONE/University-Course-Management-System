package com.ucms.identity.repository;

import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.RoleScopeAssignment;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Combines the Users list's optional filters (role tab, status filter, search box) into
 * one query instead of fetching everything and filtering in memory — the whole point of
 * paginating in the first place. Any argument left null is simply not applied.
 */
public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> filter(RoleKind role, UserStatus status, String q) {
        return filter(role, null, status, q);
    }

    /** roleId matches a user whose account role is that role, or who holds it through a
     * scope assignment (custom roles such as "Program Head" are only ever held that way). */
    public static Specification<User> filter(RoleKind role, UUID roleId, UserStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (role != null) {
                predicates.add(cb.equal(root.get("role").get("kind"), role));
            }
            if (roleId != null) {
                var sub = query.subquery(UUID.class);
                var a = sub.from(RoleScopeAssignment.class);
                sub.select(a.get("id")).where(
                        cb.equal(a.get("user"), root),
                        cb.equal(a.get("role").get("id"), roleId),
                        cb.isNull(a.get("deletedAt")));
                predicates.add(cb.or(cb.equal(root.get("role").get("id"), roleId), cb.exists(sub)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
