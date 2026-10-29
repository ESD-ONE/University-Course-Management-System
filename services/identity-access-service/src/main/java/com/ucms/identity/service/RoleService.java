package com.ucms.identity.service;

import com.ucms.identity.domain.Permission;
import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RolePermission;
import com.ucms.identity.exception.DuplicateRoleNameException;
import com.ucms.identity.exception.StaleRoleVersionException;
import com.ucms.identity.repository.PermissionRepository;
import com.ucms.identity.repository.RolePermissionRepository;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.web.dto.CreateRoleRequest;
import com.ucms.identity.web.dto.PermissionResponse;
import com.ucms.identity.web.dto.RolePermissionGrant;
import com.ucms.identity.web.dto.RoleResponse;
import com.ucms.identity.web.dto.UpdateRolePermissionRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Finally puts the roles/permissions/role_permissions tables (migrated since
 * identity-access-service's V1, never read or written until now) to use. See
 * docs/SYSTEM_ARCHITECTURE_REPORT.md section 5 / 12.1.
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                        RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .filter(r -> r.getDeletedAt() == null)
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> RoleResponse.from(r, grantsFor(r.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByModuleAscActionKeyAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request, UUID createdBy) {
        // G-03: name is the uniqueness boundary, not kind — multiple roles (e.g. a custom
        // "Department Head" and the seeded "Lecturer") can legitimately share a kind.
        // Rejecting duplicate names here closes the root cause of the kind-collision
        // incident, rather than only patching the lookup that surfaced it.
        if (roleRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new DuplicateRoleNameException("A role named '" + request.name() + "' already exists");
        }
        Role role = new Role();
        role.setName(request.name());
        role.setKind(request.kind());
        role.setCreatedBy(createdBy);
        role.setUpdatedBy(createdBy);
        role = roleRepository.save(role);
        return RoleResponse.from(role, List.of());
    }

    @Transactional
    public RoleResponse updateRolePermission(UUID roleId, UpdateRolePermissionRequest request, UUID updatedBy) {
        Role role = roleRepository.findById(roleId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));

        // G-10: wire the DTO's version field up for real. A null version (the field is
        // optional) means the caller isn't opting into this check — only reject when a
        // version was actually supplied and it no longer matches the role's current one.
        if (request.version() != null && !request.version().equals(role.getVersion())) {
            throw new StaleRoleVersionException(
                    "This role was modified by someone else — reload and try again.");
        }

        Permission permission = permissionRepository.findById(request.permissionId())
                .orElseThrow(() -> new EntityNotFoundException("Permission not found"));

        RolePermission grant = rolePermissionRepository.findByRoleId(roleId).stream()
                .filter(rp -> rp.getPermission().getId().equals(permission.getId()))
                .findFirst()
                .orElseGet(() -> {
                    RolePermission rp = new RolePermission();
                    rp.setRole(role);
                    rp.setPermission(permission);
                    return rp;
                });
        grant.setGranted(request.granted());
        grant.setCreatedBy(grant.getCreatedBy() == null ? updatedBy : grant.getCreatedBy());
        grant.setUpdatedBy(updatedBy);
        rolePermissionRepository.save(grant);

        return RoleResponse.from(role, grantsFor(roleId));
    }

    private List<RolePermissionGrant> grantsFor(UUID roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(rp -> new RolePermissionGrant(rp.getPermission().getId(), rp.getPermission().getModule(),
                        rp.getPermission().getActionKey(), rp.isGranted()))
                .toList();
    }
}
