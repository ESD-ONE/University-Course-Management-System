package com.ucms.identity.repository;

import com.ucms.identity.domain.RolePermission;
import com.ucms.identity.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByRoleId(UUID roleId);
}
