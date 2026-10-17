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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Backs the Profile screen (html-wireframes/index.html profile(), lines 2199-2234) —
 * the one view in docs/FULL_SYSTEM_GAP_ANALYSIS.md fully achievable without a new
 * service. Each endpoint acts only on the caller's own account (no :id path param),
 * so there's no risk of one user editing another's name/password/preferences.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileSelfServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void seed() throws Exception {
        Role student = new Role();
        student.setName("Student");
        student.setKind(RoleKind.STUDENT);
        roleRepository.save(student);

        User user = new User();
        user.setName("Original Name");
        user.setEmail("profile@test.local");
        user.setPasswordHash(passwordEncoder.encode("OriginalPass1!"));
        user.setRole(student);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"profile@test.local\",\"password\":\"OriginalPass1!\"}"))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void getProfile_includesDefaultNotificationPreferences() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyEmail").value(true))
                .andExpect(jsonPath("$.notifyAssignments").value(true))
                .andExpect(jsonPath("$.notifyGrades").value(true))
                .andExpect(jsonPath("$.notifyRegistrations").value(true));
    }

    @Test
    void updateName_changesTheDisplayedName() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Updated Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updatePassword_withCorrectCurrentPassword_succeedsAndNewPasswordWorks() throws Exception {
        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"OriginalPass1!\",\"newPassword\":\"BrandNewPass2!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"profile@test.local\",\"password\":\"BrandNewPass2!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void updatePassword_withWrongCurrentPassword_isRejectedAndOldPasswordStillWorks() throws Exception {
        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"WrongCurrent1!\",\"newPassword\":\"BrandNewPass2!\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"profile@test.local\",\"password\":\"OriginalPass1!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updatePassword_tooShort_isRejected() throws Exception {
        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"OriginalPass1!\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateNotificationPreferences_persistsEachFlagIndependently() throws Exception {
        mockMvc.perform(patch("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"email\":false,\"assignments\":true,\"grades\":false,\"registrations\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyEmail").value(false))
                .andExpect(jsonPath("$.notifyAssignments").value(true))
                .andExpect(jsonPath("$.notifyGrades").value(false))
                .andExpect(jsonPath("$.notifyRegistrations").value(true));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.notifyEmail").value(false))
                .andExpect(jsonPath("$.notifyGrades").value(false));
    }

    @Test
    void profileEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me").contentType("application/json").content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/users/me/password").contentType("application/json")
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"bbbbbbbb\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/users/me/notification-preferences").contentType("application/json")
                        .content("{\"email\":true,\"assignments\":true,\"grades\":true,\"registrations\":true}"))
                .andExpect(status().isForbidden());
    }
}
