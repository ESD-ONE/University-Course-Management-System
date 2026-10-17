package com.ucms.identity.repository;

import com.ucms.identity.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrderByModuleAscActionKeyAsc();
    Optional<Permission> findByModuleAndActionKey(String module, String actionKey);
}
