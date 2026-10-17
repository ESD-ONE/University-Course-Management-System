package com.ucms.identity.service;

import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserSession;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.exception.AccountNotActiveException;
import com.ucms.identity.exception.InvalidCredentialsException;
import com.ucms.identity.repository.UserRepository;
import com.ucms.identity.repository.UserSessionRepository;
import com.ucms.identity.security.JwtService;
import com.ucms.identity.web.dto.LoginRequest;
import com.ucms.identity.web.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * Mirrors flow-04-login.mmd: look up by email, verify hash, branch on credential
 * validity and account status, issue a JWT and persist the session record.
 * Gateway-side Redis caching of validated tokens is intentionally out of scope here
 * (see JwtAuthFilter) — this service remains the source of truth for its own tokens.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                        UserSessionRepository sessionRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Incorrect email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Incorrect email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException("This account is inactive — contact an administrator");
        }

        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole().getKind().name());

        UserSession session = new UserSession();
        session.setUser(user);
        session.setTokenHash(hash(token));
        session.setExpiresAt(jwtService.expiryOf(token).atOffset(java.time.ZoneOffset.UTC));
        sessionRepository.save(session);

        return new LoginResponse(token, user.getRole().getKind().name(), user.getName());
    }

    private String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
