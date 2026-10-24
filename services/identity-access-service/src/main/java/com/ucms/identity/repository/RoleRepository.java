package com.ucms.identity.repository;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    /**
     * Resolves "the" system role for a kind, used when assigning a user's base
     * (legacy, flat) role — never a custom role. A plain findByKindAndDeletedAtIsNull
     * throws NonUniqueResultException once a custom role shares a kind with a system
     * role (e.g. a "Department Head" custom role created with kind=LECTURER, same as
     * the seeded "Lecturer" role) — a real case, not hypothetical, since nothing stops
     * an admin creating a custom role with any kind via POST /roles. Ordering by
     * created_at picks the seeded row deterministically: it's always created first,
     * by the V2 migration, before any custom role can exist via the API.
     */
    Optional<Role> findFirstByKindAndDeletedAtIsNullOrderByCreatedAtAsc(RoleKind kind);
}
