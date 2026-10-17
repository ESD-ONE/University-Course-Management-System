package com.ucms.identity.repository;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByKindAndDeletedAtIsNull(RoleKind kind);
}
