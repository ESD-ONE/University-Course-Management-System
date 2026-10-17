package com.ucms.identity.service;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.exception.EmailAlreadyInUseException;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.repository.UserRepository;
import com.ucms.identity.repository.UserSpecifications;
import com.ucms.identity.web.dto.CreateUserRequest;
import com.ucms.identity.web.dto.CreatedUserResponse;
import com.ucms.identity.web.dto.UserResponse;
import com.ucms.identity.web.dto.UserSummaryResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Year;
import java.util.UUID;

/**
 * Users are always admin-created and admin-assigned a role (no self-service role picker) —
 * matches the earlier UX decision that was deliberately corrected away from self-service
 * sign-in-as. See docs/ARCHITECTURE_ANALYSIS.md.
 *
 * DTO mapping happens inside these @Transactional methods, not in the controller: User.role
 * is lazily fetched, and with open-in-view disabled the Hibernate session is gone by the
 * time the controller would otherwise touch it, which throws LazyInitializationException.
 */
@Service
public class UserService {

    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 14;
    private static final int STUDENT_ID_MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CreatedUserResponse createUser(CreateUserRequest request, UUID createdBy) {
        userRepository.findByEmailAndDeletedAtIsNull(request.email()).ifPresent(u -> {
            throw new EmailAlreadyInUseException("A user with this email already exists");
        });

        UserStatus status = request.status() == null ? UserStatus.ACTIVE : request.status();
        if (status == UserStatus.SUSPENDED) {
            throw new IllegalArgumentException(
                    "A new user can only be created as ACTIVE or INACTIVE — suspend it afterwards if needed");
        }

        Role role = roleRepository.findByKindAndDeletedAtIsNull(request.role())
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + request.role()));

        String rawPassword = request.password();
        String temporaryPassword = null;
        if (rawPassword == null || rawPassword.isBlank()) {
            temporaryPassword = generateTemporaryPassword();
            rawPassword = temporaryPassword;
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        if (request.role() == RoleKind.STUDENT) {
            user.setStudentId(generateUniqueStudentId());
        }

        return CreatedUserResponse.from(userRepository.save(user), temporaryPassword);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(RoleKind role, UserStatus status, String q, Pageable pageable) {
        return userRepository.findAll(UserSpecifications.filter(role, status, q), pageable)
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse summary() {
        return new UserSummaryResponse(
                userRepository.countActiveRecords(),
                userRepository.countByStatusActive(),
                userRepository.countStudents(),
                userRepository.countStaff());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(findActiveOrThrow(id));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status, UUID updatedBy) {
        User user = findActiveOrThrow(id);
        user.setStatus(status);
        user.setUpdatedBy(updatedBy);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateRole(UUID id, RoleKind roleKind, UUID updatedBy) {
        User user = findActiveOrThrow(id);
        Role role = roleRepository.findByKindAndDeletedAtIsNull(roleKind)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleKind));
        user.setRole(role);
        user.setUpdatedBy(updatedBy);
        if (roleKind == RoleKind.STUDENT && user.getStudentId() == null) {
            user.setStudentId(generateUniqueStudentId());
        }
        return UserResponse.from(userRepository.save(user));
    }

    private User findActiveOrThrow(UUID id) {
        return userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(secureRandom.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Format STU-<year>-<4 digits>, matching the wireframe's auto-generated pattern. */
    private String generateUniqueStudentId() {
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < STUDENT_ID_MAX_ATTEMPTS; attempt++) {
            String candidate = "STU-%d-%04d".formatted(year, secureRandom.nextInt(10_000));
            if (!userRepository.existsByStudentId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique student ID after "
                + STUDENT_ID_MAX_ATTEMPTS + " attempts");
    }
}
