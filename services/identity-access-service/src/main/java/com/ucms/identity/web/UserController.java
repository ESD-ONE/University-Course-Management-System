package com.ucms.identity.web;

import com.ucms.identity.service.UserService;
import com.ucms.identity.web.dto.CreateUserRequest;
import com.ucms.identity.web.dto.CreatedUserResponse;
import com.ucms.identity.web.dto.UpdateUserRoleRequest;
import com.ucms.identity.web.dto.UpdateUserStatusRequest;
import com.ucms.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    public ResponseEntity<CreatedUserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request, currentUserId));
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return userService.listUsers();
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
