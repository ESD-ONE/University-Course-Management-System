package com.ucms.identity.web;

import com.ucms.identity.service.AssignmentService;
import com.ucms.identity.web.dto.AssignmentResponse;
import com.ucms.identity.web.dto.CreateAssignmentRequest;
import com.ucms.identity.web.dto.SetOverrideRequest;
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

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<Void> revoke(@PathVariable("assignmentId") UUID assignmentId,
                                        @AuthenticationPrincipal UUID currentUserId) {
        assignmentService.revoke(assignmentId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
