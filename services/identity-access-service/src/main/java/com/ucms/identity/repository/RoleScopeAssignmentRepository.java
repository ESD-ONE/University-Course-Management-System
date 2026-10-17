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
}
