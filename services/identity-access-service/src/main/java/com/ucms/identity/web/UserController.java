package com.ucms.identity.web;

import com.ucms.identity.service.UserService;
import com.ucms.identity.web.dto.CreateUserRequest;
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
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                    @AuthenticationPrincipal UUID currentUserId) {
        var created = userService.createUser(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return userService.listUsers().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/users/me")
    public UserResponse me(@AuthenticationPrincipal UUID currentUserId) {
        return UserResponse.from(userService.getById(currentUserId));
    }
}
