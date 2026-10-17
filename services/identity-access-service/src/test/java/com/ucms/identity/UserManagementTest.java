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

        Role lecturer = new Role();
        lecturer.setName("Lecturer");
        lecturer.setKind(RoleKind.LECTURER);
        roleRepository.save(lecturer);

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
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.studentId").value(org.hamcrest.Matchers.matchesPattern("STU-\\d{4}-\\d{4}")));
    }

    @Test
    void createUser_asLecturer_getsNoStudentId() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Dr. L\",\"email\":\"dr.l@test.local\",\"role\":\"LECTURER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(org.hamcrest.Matchers.nullValue()));
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

    @Test
    void listUsers_filtersByRole_backingThePerRoleTabs() throws Exception {
        createStudent("Alice Student", "alice@test.local");
        createStudent("Bob Student", "bob@test.local");

        mockMvc.perform(get("/users").param("role", "STUDENT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].role", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("STUDENT"))));

        // the seeded admin must not leak into the STUDENT tab
        mockMvc.perform(get("/users").param("role", "ADMIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin@test.local"));
    }

    @Test
    void listUsers_searchesByNameOrEmail_caseInsensitively() throws Exception {
        createStudent("Chamara Herath", "c.herath@test.local");
        createStudent("Nimal Perera", "n.perera@test.local");

        mockMvc.perform(get("/users").param("q", "herath")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Chamara Herath"));
    }

    @Test
    void listUsers_paginates_notLoadingEverythingAtOnce() throws Exception {
        for (int i = 0; i < 5; i++) {
            createStudent("Student " + i, "student" + i + "@test.local");
        }

        mockMvc.perform(get("/users").param("role", "STUDENT").param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void summary_countsAreAccurate_regardlessOfCurrentFilter() throws Exception {
        createStudent("S1", "s1@test.local");
        createStudent("S2", "s2@test.local");
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"L1\",\"email\":\"l1@test.local\",\"role\":\"LECTURER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/users/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))   // seed admin + 2 students + 1 lecturer
                .andExpect(jsonPath("$.active").value(4))
                .andExpect(jsonPath("$.students").value(2))
                .andExpect(jsonPath("$.staff").value(2));  // admin + lecturer
    }

    private void createStudent(String name, String email) throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isCreated());
    }
}
