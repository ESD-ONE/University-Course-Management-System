package com.ucms.identity.service;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.RoleScopeAssignment;
import com.ucms.identity.domain.ScopeType;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.exception.EmailAlreadyInUseException;
import com.ucms.identity.exception.WrongPasswordException;
import com.ucms.identity.keycloak.KeycloakUserSync;
import com.ucms.identity.messaging.EventPublisher;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.repository.RoleScopeAssignmentRepository;
import com.ucms.identity.repository.UserRepository;
import com.ucms.identity.repository.UserSpecifications;
import com.ucms.identity.security.AssignmentCacheInvalidator;
import com.ucms.identity.web.dto.AssignmentResponse;
import com.ucms.identity.web.dto.CreateUserRequest;
import com.ucms.identity.web.dto.CreatedUserResponse;
import com.ucms.identity.web.dto.ProfileResponse;
import com.ucms.identity.web.dto.UpdateNotificationPreferencesRequest;
import com.ucms.identity.web.dto.UserResponse;
import com.ucms.identity.web.dto.UserSummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.List;
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

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleScopeAssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;
    private final KeycloakUserSync keycloak;
    private final AssignmentCacheInvalidator assignmentCache;
    private final SecureRandom secureRandom = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                        RoleScopeAssignmentRepository assignmentRepository, PasswordEncoder passwordEncoder,
                        EventPublisher eventPublisher, KeycloakUserSync keycloak,
                        AssignmentCacheInvalidator assignmentCache) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.keycloak = keycloak;
        this.assignmentCache = assignmentCache;
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

        Role role = roleRepository.findFirstByKindAndDeletedAtIsNullOrderByCreatedAtAsc(request.role())
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

        user = userRepository.save(user);

        // Every user gets a baseline GLOBAL assignment matching their account role, so
        // they start out behaviorally equivalent to pre-RBAC accounts — same as the V5
        // migration's one-time backfill for pre-existing users. See
        // docs/SYSTEM_ARCHITECTURE_REPORT.md section 14 step 5.
        RoleScopeAssignment assignment = new RoleScopeAssignment();
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setScopeType(ScopeType.GLOBAL);
        assignment.setCreatedBy(createdBy);
        assignment.setUpdatedBy(createdBy);
        assignmentRepository.save(assignment);

        // Keycloak verifies sign-ins; the account can log in as soon as this commits.
        keycloak.created(user, rawPassword);
        user = userRepository.save(user);

        eventPublisher.publish("identity.user-created", java.util.Map.of(
                "userId", user.getId().toString(),
                "name", user.getName(),
                "role", request.role().name()));

        return CreatedUserResponse.from(user, temporaryPassword);
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
    public UserResponse getUser(UUID id) {
        return UserResponse.from(findActiveOrThrow(id));
    }

    /** Unknown or deleted ids are simply absent from the result, not an error — callers
     * resolving a roster shouldn't fail because one referenced account was removed. */
    @Transactional(readOnly = true)
    public List<UserResponse> lookup(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids).stream()
                .filter(u -> !u.isDeleted())
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID id) {
        return ProfileResponse.from(findActiveOrThrow(id), assignmentsFor(id));
    }

    @Transactional
    public ProfileResponse updateOwnName(UUID id, String name) {
        User user = findActiveOrThrow(id);
        user.setName(name);
        user.setUpdatedBy(id);
        keycloak.profileChanged(user);
        return ProfileResponse.from(userRepository.save(user), assignmentsFor(id));
    }

    @Transactional
    public ProfileResponse updateOwnPassword(UUID id, String currentPassword, String newPassword) {
        User user = findActiveOrThrow(id);
        // Keycloak holds the password that actually signs the user in, so it's the one
        // checked; the local hash is kept in step for an account that ever needs re-linking.
        if (!keycloak.passwordMatches(user, currentPassword)) {
            throw new WrongPasswordException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedBy(id);
        keycloak.passwordChanged(user, newPassword);
        return ProfileResponse.from(userRepository.save(user), assignmentsFor(id));
    }

    @Transactional
    public ProfileResponse updateNotificationPreferences(UUID id, UpdateNotificationPreferencesRequest prefs) {
        User user = findActiveOrThrow(id);
        user.setNotifyEmail(prefs.email());
        user.setNotifyAssignments(prefs.assignments());
        user.setNotifyGrades(prefs.grades());
        user.setNotifyRegistrations(prefs.registrations());
        user.setUpdatedBy(id);
        return ProfileResponse.from(userRepository.save(user), assignmentsFor(id));
    }

    /**
     * Gap G-01's backend half: {@code ProfileResponse.assignments} needs this on every
     * self-service read, not just a dedicated endpoint, since the frontend decides nav
     * visibility (e.g. Program Management for a DEPARTMENT-scoped Lecturer) off
     * GET /users/me's response. Overrides are omitted (always empty) — see
     * ProfileResponse's own Javadoc for why.
     */
    private List<AssignmentResponse> assignmentsFor(UUID userId) {
        return assignmentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(a -> AssignmentResponse.from(a, List.of()))
                .toList();
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status, UUID updatedBy) {
        User user = findActiveOrThrow(id);
        user.setStatus(status);
        user.setUpdatedBy(updatedBy);
        keycloak.statusChanged(user);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateRole(UUID id, RoleKind roleKind, UUID updatedBy) {
        User user = findActiveOrThrow(id);
        Role role = roleRepository.findFirstByKindAndDeletedAtIsNullOrderByCreatedAtAsc(roleKind)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleKind));
        user.setRole(role);
        user.setUpdatedBy(updatedBy);
        if (roleKind == RoleKind.STUDENT && user.getStudentId() == null) {
            user.setStudentId(generateUniqueStudentId());
        }
        User savedUser = userRepository.save(user);

        // G-04: the flat role and the GLOBAL scope assignment created at user-creation
        // time (see createUser()) used to drift apart on every admin-initiated role
        // change, leaving academic-structure-service's PermissionResolver resolving
        // against the stale role until someone separately fixed the assignment by hand.
        RoleScopeAssignment globalAssignment = assignmentRepository
                .findFirstByUserIdAndScopeTypeAndDeletedAtIsNullOrderByCreatedAtAsc(id, ScopeType.GLOBAL)
                .orElseGet(() -> {
                    RoleScopeAssignment a = new RoleScopeAssignment();
                    a.setUser(savedUser);
                    a.setScopeType(ScopeType.GLOBAL);
                    a.setCreatedBy(updatedBy);
                    return a;
                });
        globalAssignment.setRole(role);
        globalAssignment.setUpdatedBy(updatedBy);
        assignmentRepository.save(globalAssignment);
        assignmentCache.evict(id);
        keycloak.roleChanged(savedUser);

        return UserResponse.from(savedUser);
    }

    @Transactional
    public void deleteUser(UUID id, UUID deletedBy) {
        User user = findActiveOrThrow(id);
        user.setDeletedAt(OffsetDateTime.now());
        user.setDeletedBy(deletedBy);
        user.setUpdatedBy(deletedBy);
        keycloak.deleted(user);
        user.setKeycloakId(null);
        userRepository.save(user);
        assignmentCache.evict(id);
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

    /**
     * Format STU-<year>-<4 digits>, matching the wireframe's auto-generated pattern —
     * unchanged from before. G-16: generation itself used to be a 10-attempt random
     * retry loop checking existsByStudentId, which left a narrow race window under
     * concurrent creation (two requests could each pass the check before either
     * committed). A database sequence (student_id_seq — see the V6 Flyway migration)
     * makes allocation atomic instead: every call gets a distinct value directly from
     * Postgres, with no check-then-act gap. This is purely an internal mechanism change;
     * the 4-digit zero-padded suffix is still, as before, only unique up to 10,000
     * values — an accepted, pre-existing format limitation, not something this fix
     * redesigns.
     */
    private String generateUniqueStudentId() {
        int year = Year.now().getValue();
        Number sequenceValue = (Number) entityManager
                .createNativeQuery("select nextval('identity_access.student_id_seq')")
                .getSingleResult();
        long next = sequenceValue.longValue();
        return "STU-%d-%04d".formatted(year, next % 10_000);
    }
}
