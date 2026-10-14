package com.ucms.identity.service;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.exception.EmailAlreadyInUseException;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.repository.UserRepository;
import com.ucms.identity.web.dto.CreateUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Users are always admin-created and admin-assigned a role (no self-service role picker) —
 * matches the earlier UX decision that was deliberately corrected away from self-service
 * sign-in-as. See docs/ARCHITECTURE_ANALYSIS.md.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(CreateUserRequest request, UUID createdBy) {
        userRepository.findByEmailAndDeletedAtIsNull(request.email()).ifPresent(u -> {
            throw new EmailAlreadyInUseException("A user with this email already exists");
        });

        Role role = roleRepository.findByKindAndDeletedAtIsNull(request.role())
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + request.role()));

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);

        return userRepository.save(user);
    }

    public List<User> listUsers() {
        return userRepository.findAll().stream().filter(u -> !u.isDeleted()).toList();
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));
    }
}
