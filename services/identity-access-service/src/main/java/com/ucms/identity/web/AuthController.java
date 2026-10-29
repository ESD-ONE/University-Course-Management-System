package com.ucms.identity.web;

import com.ucms.identity.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login is Keycloak's hosted page now — only logout's token revocation is left here. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * G-11: puts this exact token on the Redis revocation list the Gateway checks, so it
     * stops working immediately for every route behind the Gateway. Backend services still
     * validate statelessly — a caller that bypasses the Gateway (only possible from inside
     * the Docker network) would not see the revocation; that's the accepted boundary.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
