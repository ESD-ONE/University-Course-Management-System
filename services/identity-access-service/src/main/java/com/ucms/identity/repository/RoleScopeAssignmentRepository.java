package com.ucms.identity.repository;

import com.ucms.identity.domain.RoleScopeAssignment;
import com.ucms.identity.domain.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleScopeAssignmentRepository extends JpaRepository<RoleScopeAssignment, UUID> {
    List<RoleScopeAssignment> findByUserIdAndDeletedAtIsNull(UUID userId);
    List<RoleScopeAssignment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    Optional<RoleScopeAssignment> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByUserIdAndScopeTypeAndDeletedAtIsNull(UUID userId, ScopeType scopeType);
    long countByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * Used by {@code UserService.updateRole()} (G-04) to keep a user's GLOBAL scope
     * assignment in sync with their flat role after an admin-initiated role change.
     * Deliberately "findFirst...OrderByCreatedAtAsc" rather than a single-result
     * "findBy...", following the same defensive pattern the G-03 fix adopted for
     * roles: nothing here prevents a second non-deleted GLOBAL assignment for the same
     * user (POST /users/{id}/assignments has no such uniqueness guard), so a plain
     * single-result query would risk the exact NonUniqueResultException G-03 fixed.
     */
    Optional<RoleScopeAssignment> findFirstByUserIdAndScopeTypeAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID userId, ScopeType scopeType);
}
