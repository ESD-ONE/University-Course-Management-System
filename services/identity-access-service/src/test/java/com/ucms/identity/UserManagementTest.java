package com.ucms.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.keycloak.KeycloakUnavailableException;
import com.ucms.identity.keycloak.KeycloakUserSync;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
    @MockBean KeycloakUserSync keycloakUserSync;

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
        adminUser = userRepository.save(adminUser);
        adminToken = KeycloakTestTokens.user(adminUser.getId(), "ADMIN");
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
    void createUser_withAShortPassword_isRejectedWithAPlainMessage() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"T. Short\",\"email\":\"t.short@test.local\",\"password\":\"Short-pw1\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed: Password must be 12 to 128 characters"));
    }

    @Test
    void createUser_createsTheKeycloakAccountWithTheSamePasswordItReturns() throws Exception {
        String body = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"K. Perera\",\"email\":\"k.perera@test.local\",\"role\":\"LECTURER\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = objectMapper.readTree(body).get("temporaryPassword").asText();

        verify(keycloakUserSync).created(argThat(u -> u.getEmail().equals("k.perera@test.local")), eq(temporaryPassword));
    }

    @Test
    void createUser_isRolledBackWhenKeycloakIsUnavailable() throws Exception {
        doThrow(new KeycloakUnavailableException("down", null)).when(keycloakUserSync).created(any(), any());
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Q\",\"email\":\"q@test.local\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isServiceUnavailable());
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

        // Keycloak is told, so the account can no longer sign in.
        verify(keycloakUserSync).statusChanged(argThat(u -> u.getStatus() == UserStatus.SUSPENDED));
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

    // ---- G-03/G-03b: role kind-collision no longer breaks user creation ----

    @Test
    void createRole_sharingKindWithSeededRole_doesNotBreakUserCreation() throws Exception {
        // A custom role sharing a kind with the already-seeded "Lecturer" role used to
        // make findByKindAndDeletedAtIsNull throw NonUniqueResultException the moment a
        // second LECTURER-kind role existed, surfacing as a 403/500 on user creation.
        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Department Head\",\"kind\":\"LECTURER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"New Lecturer\",\"email\":\"new.lecturer@test.local\",\"role\":\"LECTURER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("LECTURER"));
    }

    @Test
    void createRole_duplicateName_isRejectedWith409() throws Exception {
        // "Lecturer" already exists from @BeforeEach's seed data.
        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Lecturer\",\"kind\":\"LECTURER\"}"))
                .andExpect(status().isConflict());
    }

    // ---- G-04: role change syncs the user's GLOBAL scope assignment ----

    @Test
    void updateRole_syncsTheUsersGlobalScopeAssignment() throws Exception {
        String body = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"V\",\"email\":\"v@test.local\",\"role\":\"STUDENT\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(put("/users/" + id + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(get("/users/" + id + "/assignments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scopeType").value("GLOBAL"))
                .andExpect(jsonPath("$[0].roleName").value("Administrator"));
    }

    // ---- G-12: DELETE /users/{id} soft-deletes and removes them from listings ----

    @Test
    void deleteUser_removesThemFromTheUsersListing() throws Exception {
        String body = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"To Delete\",\"email\":\"to.delete@test.local\",\"role\":\"STUDENT\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(delete("/users/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users").param("q", "to.delete@test.local")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ---- G-16: student ID generation is now atomic (DB sequence), not random-retry ----

    @Test
    void createStudents_backToBack_getDistinctStudentIds() throws Exception {
        String first = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"First Student\",\"email\":\"first.student@test.local\",\"role\":\"STUDENT\"}"))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Second Student\",\"email\":\"second.student@test.local\",\"role\":\"STUDENT\"}"))
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(first).get("studentId").asText();
        String secondId = objectMapper.readTree(second).get("studentId").asText();

        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
        org.hamcrest.MatcherAssert.assertThat(firstId, org.hamcrest.Matchers.matchesPattern("STU-\\d{4}-\\d{4}"));
        org.hamcrest.MatcherAssert.assertThat(secondId, org.hamcrest.Matchers.matchesPattern("STU-\\d{4}-\\d{4}"));
    }
}
