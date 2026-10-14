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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises flow-04-login.mmd end to end: valid credentials, wrong password,
 * and a suspended account, against an in-memory H2 database (application-test.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        Role student = new Role();
        student.setName("Student");
        student.setKind(RoleKind.STUDENT);
        roleRepository.save(student);

        User active = new User();
        active.setName("Active Student");
        active.setEmail("active@ucms.local");
        active.setPasswordHash(passwordEncoder.encode("CorrectPass1!"));
        active.setRole(student);
        active.setStatus(UserStatus.ACTIVE);
        userRepository.save(active);

        User suspended = new User();
        suspended.setName("Suspended Student");
        suspended.setEmail("suspended@ucms.local");
        suspended.setPasswordHash(passwordEncoder.encode("CorrectPass1!"));
        suspended.setRole(student);
        suspended.setStatus(UserStatus.SUSPENDED);
        userRepository.save(suspended);
    }

    @Test
    void validCredentials_returnsTokenAndRole() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"active@ucms.local\",\"password\":\"CorrectPass1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"active@ucms.local\",\"password\":\"WrongPassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedAccount_returns403() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"suspended@ucms.local\",\"password\":\"CorrectPass1!\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownEmail_returns401NotLeakingWhichFieldWasWrong() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@ucms.local\",\"password\":\"CorrectPass1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMe_withValidToken_returnsOwnProfileIncludingRole() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"active@ucms.local\",\"password\":\"CorrectPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("active@ucms.local"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void usersMe_withNoToken_isRejected() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isForbidden());
    }
}
