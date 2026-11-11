package com.ucms.identity.repository;

import com.ucms.identity.domain.RoleScopeAssignment;
import com.ucms.identity.domain.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleScopeAssignmentRepository extends JpaRepository<RoleScopeAssignment, UUID> {
    List<RoleScopeAssignment> findByUserIdAndDeletedAtIsNull(UUID userId);
    List<RoleScopeAssignment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    Optional<RoleScopeAssignment> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByUserIdAndScopeTypeAndDeletedAtIsNull(UUID userId, ScopeType scopeType);
    long countByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("""
            select a from RoleScopeAssignment a join fetch a.user join fetch a.role
            where a.deletedAt is null and a.role.id = :roleId and a.scopeType = :scopeType and a.scopeId in :scopeIds
            """)
    List<RoleScopeAssignment> findHolders(@Param("roleId") UUID roleId, @Param("scopeType") ScopeType scopeType,
                                          @Param("scopeIds") Collection<UUID> scopeIds);

    /** Row-locked so two concurrent "set holder" calls can't both leave a holder behind. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from RoleScopeAssignment a
            where a.deletedAt is null and a.role.id = :roleId and a.scopeType = :scopeType and a.scopeId = :scopeId
            """)
    List<RoleScopeAssignment> lockHolders(@Param("roleId") UUID roleId, @Param("scopeType") ScopeType scopeType,
                                          @Param("scopeId") UUID scopeId);

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
