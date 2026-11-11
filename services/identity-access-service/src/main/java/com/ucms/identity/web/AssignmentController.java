package com.ucms.identity.web;

import com.ucms.identity.service.AssignmentService;
import com.ucms.identity.web.dto.AssignmentResponse;
import com.ucms.identity.web.dto.CreateAssignmentRequest;
import com.ucms.identity.web.dto.EffectivePermission;
import com.ucms.identity.domain.ScopeType;
import com.ucms.identity.web.dto.ScopeHolderResponse;
import com.ucms.identity.web.dto.SetOverrideRequest;
import com.ucms.identity.web.dto.SetScopeHolderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/users/{userId}/assignments")
    public List<AssignmentResponse> listForUser(@PathVariable("userId") UUID userId) {
        return assignmentService.listForUser(userId);
    }

    @PostMapping("/users/{userId}/assignments")
    public ResponseEntity<AssignmentResponse> create(@PathVariable("userId") UUID userId,
                                                       @Valid @RequestBody CreateAssignmentRequest request,
                                                       @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.create(userId, request, currentUserId));
    }

    @PatchMapping("/assignments/{assignmentId}/overrides")
    public AssignmentResponse setOverride(@PathVariable("assignmentId") UUID assignmentId,
                                           @Valid @RequestBody SetOverrideRequest request,
                                           @AuthenticationPrincipal UUID currentUserId) {
        return assignmentService.setOverride(assignmentId, request, currentUserId);
    }

    @GetMapping("/users/me/permissions")
    public List<EffectivePermission> myPermissions(@AuthenticationPrincipal UUID currentUserId) {
        return assignmentService.effectivePermissions(currentUserId);
    }

    /** Readable by any signed-in account (lists show "Program Head: …"); changing it is
     *  admin-only (SecurityConfig: /assignments/**). */
    @GetMapping("/assignments/scope-holders")
    public List<ScopeHolderResponse> holders(@RequestParam(name = "roleId") UUID roleId,
                                             @RequestParam(name = "scopeType") ScopeType scopeType,
                                             @RequestParam(name = "scopeIds") List<UUID> scopeIds) {
        return assignmentService.holders(roleId, scopeType, scopeIds);
    }

    @PutMapping("/assignments/holder")
    public ResponseEntity<ScopeHolderResponse> setHolder(@Valid @RequestBody SetScopeHolderRequest request,
                                                         @AuthenticationPrincipal UUID currentUserId) {
        ScopeHolderResponse holder = assignmentService.setHolder(request, currentUserId);
        return holder == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(holder);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<Void> revoke(@PathVariable("assignmentId") UUID assignmentId,
                                        @AuthenticationPrincipal UUID currentUserId) {
        assignmentService.revoke(assignmentId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
