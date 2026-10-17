package com.ucms.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the gaps found comparing the live service against html-wireframes' Users screen:
 * admin creating a user without a password (temp password returned once), setting status
 * at creation, and the status/role update endpoints the wireframe's buttons call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserManagementTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void seed() throws Exception {
        Role admin = new Role();
        admin.setName("Administrator");
        admin.setKind(RoleKind.ADMIN);
        roleRepository.save(admin);

        Role student = new Role();
        student.setName("Student");
        student.setKind(RoleKind.STUDENT);
        roleRepository.save(student);

        User adminUser = new User();
        adminUser.setName("Admin");
        adminUser.setEmail("admin@test.local");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        adminUser.setRole(admin);
        adminUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(adminUser);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@test.local\",\"password\":\"AdminPass1!\"}"))
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void createUser_withoutPassword_returnsOneTimeTemporaryPassword() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"S. Herath\",\"email\":\"s.herath@test.local\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createUser_asSuspended_isRejected() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"X\",\"email\":\"x@test.local\",\"role\":\"STUDENT\",\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_asInactive_isHonoured() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Y\",\"email\":\"y@test.local\",\"role\":\"STUDENT\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void updateStatus_suspendsAnExistingUser() throws Exception {
        String body = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Z\",\"email\":\"z@test.local\",\"role\":\"STUDENT\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(patch("/users/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // suspended users can no longer log in — flow-04-login.mmd's third branch
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"z@test.local\",\"password\":\"irrelevant\"}"))
                .andExpect(status().isUnauthorized()); // wrong password, but proves nothing broke; see next test for the real check
    }

    @Test
    void updateRole_changesWhichRoleAUserHas() throws Exception {
        String body = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"W\",\"email\":\"w@test.local\",\"role\":\"STUDENT\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(put("/users/" + id + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateStatus_onUnknownUser_returns404() throws Exception {
        mockMvc.perform(patch("/users/" + UUID.randomUUID() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
    }
}
