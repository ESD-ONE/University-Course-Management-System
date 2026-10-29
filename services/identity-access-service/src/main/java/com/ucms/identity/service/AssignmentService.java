package com.ucms.identity.service;

import com.ucms.identity.domain.*;
import com.ucms.identity.repository.*;
import com.ucms.identity.security.AssignmentCacheInvalidator;
import com.ucms.identity.web.dto.AssignmentClaim;
import com.ucms.identity.web.dto.AssignmentResponse;
import com.ucms.identity.web.dto.CreateAssignmentRequest;
import com.ucms.identity.web.dto.RolePermissionGrant;
import com.ucms.identity.web.dto.SetOverrideRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    public AssignmentService(UserRepository userRepository, RoleRepository roleRepository,
                              RoleScopeAssignmentRepository assignmentRepository,
                              AssignmentPermissionOverrideRepository overrideRepository,
                              PermissionRepository permissionRepository,
                              AssignmentCacheInvalidator cacheInvalidator) {
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
