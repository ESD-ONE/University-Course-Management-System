package com.ucms.identity.repository;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByNameAndDeletedAtIsNull(String name);

    /**
     * {@code name} is unique (see {@link #existsByNameAndDeletedAtIsNull}), but
     * {@code kind} deliberately is not — a real production incident saw a custom
     * "Department Head" role created with {@code kind=LECTURER}, the same kind as the
     * seeded "Lecturer" role. A plain {@code findByKindAndDeletedAtIsNull} throws
     * {@code NonUniqueResultException} the moment more than one non-deleted role shares
     * a kind. This method resolves deterministically instead: the earliest-created role
     * of that kind wins, so lookups keep working no matter how many later roles share it.
     */
    Optional<Role> findFirstByKindAndDeletedAtIsNullOrderByCreatedAtAsc(RoleKind kind);

    /**
     * Row-locked so two concurrent permission changes to one role run one after the
     * other. Without it, two requests granting the same new permission both saw no
     * existing grant and both inserted one; the second hit the role_permissions primary
     * key and failed with a 500.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.id = :id and r.deletedAt is null")
    Optional<Role> lockById(@Param("id") UUID id);
}
