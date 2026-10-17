package com.ucms.identity.web;

import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.service.UserService;
import com.ucms.identity.web.dto.CreateUserRequest;
import com.ucms.identity.web.dto.CreatedUserResponse;
import com.ucms.identity.web.dto.UpdateUserRoleRequest;
import com.ucms.identity.web.dto.UpdateUserStatusRequest;
import com.ucms.identity.web.dto.UserResponse;
import com.ucms.identity.web.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class UserController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    public ResponseEntity<CreatedUserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request, currentUserId));
    }

    /**
     * role/status/q are all optional and combinable — role backs the Users screen's
     * per-role tabs, q is a case-insensitive name/email search. Unbounded listing was
     * removed deliberately: see docs/SYSTEM_GAP_ANALYSIS_AND_IMPLEMENTATION_PLAN.md C.1.
     */
    @GetMapping("/users")
    public Page<UserResponse> listUsers(@RequestParam(required = false) RoleKind role,
                                         @RequestParam(required = false) UserStatus status,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("name").ascending());
        return userService.search(role, status, q, pageable);
    }

    @GetMapping("/users/summary")
    public UserSummaryResponse summary() {
        return userService.summary();
    }

    @GetMapping("/users/me")
    public UserResponse me(@AuthenticationPrincipal UUID currentUserId) {
        return userService.getById(currentUserId);
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse updateStatus(@PathVariable("id") UUID id,
                                      @Valid @RequestBody UpdateUserStatusRequest request,
                                      @AuthenticationPrincipal UUID currentUserId) {
        return userService.updateStatus(id, request.status(), currentUserId);
    }

    @PutMapping("/users/{id}/role")
    public UserResponse updateRole(@PathVariable("id") UUID id,
                                    @Valid @RequestBody UpdateUserRoleRequest request,
                                    @AuthenticationPrincipal UUID currentUserId) {
        return userService.updateRole(id, request.role(), currentUserId);
    }
}
