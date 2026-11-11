package com.ucms.identity.service;

import com.ucms.identity.domain.*;
import com.ucms.identity.repository.*;
import com.ucms.identity.security.AssignmentCacheInvalidator;
import com.ucms.identity.web.dto.AssignmentClaim;
import com.ucms.identity.web.dto.AssignmentResponse;
import com.ucms.identity.web.dto.CreateAssignmentRequest;
import com.ucms.identity.web.dto.EffectivePermission;
import com.ucms.identity.web.dto.RolePermissionGrant;
import com.ucms.identity.web.dto.ScopeHolderResponse;
import com.ucms.identity.web.dto.SetOverrideRequest;
import com.ucms.identity.web.dto.SetScopeHolderRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages role_scope_assignments + assignment_permission_overrides — the scope layer on
 * top of the existing role_permissions baseline. See
 * docs/SYSTEM_ARCHITECTURE_REPORT.md section 5.2/5.3.
 */
@Service
public class AssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleScopeAssignmentRepository assignmentRepository;
    private final AssignmentPermissionOverrideRepository overrideRepository;
    private final PermissionRepository permissionRepository;
    private final AssignmentCacheInvalidator cacheInvalidator;
    private final RolePermissionRepository rolePermissionRepository;

    public AssignmentService(UserRepository userRepository, RoleRepository roleRepository,
                              RoleScopeAssignmentRepository assignmentRepository,
                              AssignmentPermissionOverrideRepository overrideRepository,
                              PermissionRepository permissionRepository,
                              AssignmentCacheInvalidator cacheInvalidator,
                              RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.overrideRepository = overrideRepository;
        this.permissionRepository = permissionRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listForUser(UUID userId) {
        return assignmentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(a -> AssignmentResponse.from(a, overridesFor(a.getId())))
                .toList();
    }

    @Transactional
    public AssignmentResponse create(UUID userId, CreateAssignmentRequest request, UUID createdBy) {
        if ((request.scopeType() == ScopeType.GLOBAL) != (request.scopeId() == null)) {
            throw new IllegalArgumentException("scopeId must be null for GLOBAL scope and non-null otherwise");
        }
        User user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Role role = roleRepository.findById(request.roleId())
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));

        RoleScopeAssignment a = new RoleScopeAssignment();
        a.setUser(user);
        a.setRole(role);
        a.setScopeType(request.scopeType());
        a.setScopeId(request.scopeId());
        a.setCreatedBy(createdBy);
        a.setUpdatedBy(createdBy);
        a = assignmentRepository.save(a);
        cacheInvalidator.evict(userId);
        return AssignmentResponse.from(a, List.of());
    }

    @Transactional
    public AssignmentResponse setOverride(UUID assignmentId, SetOverrideRequest request, UUID updatedBy) {
        RoleScopeAssignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        var permission = permissionRepository.findById(request.permissionId())
                .orElseThrow(() -> new EntityNotFoundException("Permission not found"));

        AssignmentPermissionOverride override = overrideRepository
                .findById(new AssignmentPermissionOverrideId(assignmentId, request.permissionId()))
                .orElseGet(AssignmentPermissionOverride::new);
        override.setAssignment(assignment);
        override.setPermission(permission);
        override.setGranted(request.granted());
        if (override.getCreatedBy() == null) override.setCreatedBy(updatedBy);
        override.setUpdatedBy(updatedBy);
        overrideRepository.save(override);
        cacheInvalidator.evict(assignment.getUser().getId());

        return AssignmentResponse.from(assignment, overridesFor(assignmentId));
    }

    @Transactional
    public void revoke(UUID assignmentId, UUID revokedBy) {
        RoleScopeAssignment assignment = assignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        assignment.setDeletedAt(OffsetDateTime.now());
        assignment.setDeletedBy(revokedBy);
        assignment.setUpdatedBy(revokedBy);
        assignmentRepository.save(assignment);
        cacheInvalidator.evict(assignment.getUser().getId());
    }

    /** Every (module, action) the user is granted, per assignment scope: the role's
     *  baseline with that assignment's overrides applied. The UI uses it to hide what the
     *  user can't do; each service still enforces its own checks. */
    @Transactional(readOnly = true)
    public List<EffectivePermission> effectivePermissions(UUID userId) {
        List<EffectivePermission> result = new ArrayList<>();
        for (RoleScopeAssignment a : assignmentRepository.findByUserIdAndDeletedAtIsNull(userId)) {
            Map<UUID, Boolean> overrides = new HashMap<>();
            overrideRepository.findByAssignmentId(a.getId())
                    .forEach(o -> overrides.put(o.getPermission().getId(), o.isGranted()));
            Map<UUID, Permission> granted = new HashMap<>();
            for (RolePermission rp : rolePermissionRepository.findByRoleId(a.getRole().getId())) {
                UUID pid = rp.getPermission().getId();
                if (overrides.getOrDefault(pid, rp.isGranted())) granted.put(pid, rp.getPermission());
            }
            overrides.forEach((pid, on) -> {
                if (on && !granted.containsKey(pid)) permissionRepository.findById(pid).ifPresent(p -> granted.put(pid, p));
            });
            for (Permission p : granted.values()) {
                result.add(new EffectivePermission(a.getScopeType().name(), a.getScopeId(), p.getModule(), p.getActionKey()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ScopeHolderResponse> holders(UUID roleId, ScopeType scopeType, Collection<UUID> scopeIds) {
        if (scopeIds == null || scopeIds.isEmpty()) return List.of();
        return assignmentRepository.findHolders(roleId, scopeType, scopeIds).stream()
                .map(ScopeHolderResponse::from).toList();
    }

    /** Makes userId the single holder of a role at a scope (e.g. a degree program's
     *  Program Head): revokes whoever held it, then assigns — or just clears it when
     *  userId is null. Idempotent when the same user already holds it. */
    @Transactional
    public ScopeHolderResponse setHolder(SetScopeHolderRequest request, UUID actorId) {
        if (request.scopeType() == ScopeType.GLOBAL) {
            throw new IllegalArgumentException("A single-holder role must be scoped, not GLOBAL");
        }
        RoleScopeAssignment kept = null;
        for (RoleScopeAssignment existing : assignmentRepository.lockHolders(request.roleId(), request.scopeType(), request.scopeId())) {
            if (kept == null && existing.getUser().getId().equals(request.userId())) {
                kept = existing;
                continue;
            }
            existing.setDeletedAt(OffsetDateTime.now());
            existing.setDeletedBy(actorId);
            existing.setUpdatedBy(actorId);
            assignmentRepository.save(existing);
            cacheInvalidator.evict(existing.getUser().getId());
        }
        if (request.userId() == null) return null;
        if (kept != null) return ScopeHolderResponse.from(kept);
        User user = userRepository.findById(request.userId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Role role = roleRepository.findById(request.roleId())
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));
        if (role.getKind() != user.getRole().getKind()) {
            throw new IllegalArgumentException("A " + role.getName() + " must be a " + role.getKind().name().toLowerCase() + " account");
        }
        RoleScopeAssignment a = new RoleScopeAssignment();
        a.setUser(user);
        a.setRole(role);
        a.setScopeType(request.scopeType());
        a.setScopeId(request.scopeId());
        a.setCreatedBy(actorId);
        a.setUpdatedBy(actorId);
        a = assignmentRepository.save(a);
        cacheInvalidator.evict(user.getId());
        return ScopeHolderResponse.from(a);
    }

    /** Served to academic-structure-service by GET /internal/assignments/{userId} (it used
     *  to be embedded in the JWT at login) — see AssignmentCacheInvalidator. */
    @Transactional(readOnly = true)
    public List<AssignmentClaim> resolveClaimsForUser(UUID userId) {
        return assignmentRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(a -> {
                    Map<UUID, Boolean> overrides = new HashMap<>();
                    overrideRepository.findByAssignmentId(a.getId())
                            .forEach(o -> overrides.put(o.getPermission().getId(), o.isGranted()));
                    return new AssignmentClaim(a.getId(), a.getRole().getId(), a.getScopeType().name(),
                            a.getScopeId(), overrides);
                })
                .toList();
    }

    private List<RolePermissionGrant> overridesFor(UUID assignmentId) {
        return overrideRepository.findByAssignmentId(assignmentId).stream()
                .map(o -> new RolePermissionGrant(o.getPermission().getId(), o.getPermission().getModule(),
                        o.getPermission().getActionKey(), o.isGranted()))
                .toList();
    }
}
