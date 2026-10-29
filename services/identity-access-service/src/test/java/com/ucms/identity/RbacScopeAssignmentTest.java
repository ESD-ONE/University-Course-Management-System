package com.ucms.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.domain.*;
import com.ucms.identity.keycloak.KeycloakUserSync;
import com.ucms.identity.repository.*;
import com.ucms.identity.security.AssignmentCacheInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * docs/SYSTEM_ARCHITECTURE_REPORT.md Phase 2 — the role_scope_assignments /
 * assignment_permission_overrides layer on top of the existing roles/permissions/
 * role_permissions tables. Covers docs/NEW_PHASES_TEST_CASES_REPORT.md's TC-P2-* cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacScopeAssignmentTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean KeycloakUserSync keycloakUserSync;
    @MockBean AssignmentCacheInvalidator assignmentCache;

    private String adminToken;
    private String lecturerToken;
    private Role lecturerRole;
    private User lecturerUser;
    private Permission viewCoursesPermission;

    @BeforeEach
    void seed() throws Exception {
        Role admin = new Role();
        admin.setName("Administrator");
        admin.setKind(RoleKind.ADMIN);
        roleRepository.save(admin);

        lecturerRole = new Role();
        lecturerRole.setName("Lecturer");
        lecturerRole.setKind(RoleKind.LECTURER);
        lecturerRole = roleRepository.save(lecturerRole);

        User adminUser = new User();
        adminUser.setName("Admin");
        adminUser.setEmail("admin@test.local");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        adminUser.setRole(admin);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);

        lecturerUser = new User();
        lecturerUser.setName("Dr. Silva");
        lecturerUser.setEmail("lecturer@test.local");
        lecturerUser.setPasswordHash(passwordEncoder.encode("LecturerPass1!"));
        lecturerUser.setRole(lecturerRole);
        lecturerUser.setStatus(UserStatus.ACTIVE);
        lecturerUser = userRepository.save(lecturerUser);

        viewCoursesPermission = new Permission();
        viewCoursesPermission.setModule("Courses");
        viewCoursesPermission.setActionKey("View");
        viewCoursesPermission = permissionRepository.save(viewCoursesPermission);

        adminToken = KeycloakTestTokens.user(adminUser.getId(), "ADMIN");
        lecturerToken = KeycloakTestTokens.user(lecturerUser.getId(), "LECTURER");
    }

    // ---- TC-P2-012/013: GET /roles, GET /permissions ----

    @Test
    void listRoles_asAdmin_succeeds() throws Exception {
        mockMvc.perform(get("/roles").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listRoles_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(get("/roles").header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPermissions_asAdmin_succeeds() throws Exception {
        mockMvc.perform(get("/permissions").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].module").value("Courses"));
    }

    // ---- TC-P2-014: POST /roles ----

    @Test
    void createRole_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Department Head\",\"kind\":\"LECTURER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Department Head"))
                .andExpect(jsonPath("$.kind").value("LECTURER"));
    }

    @Test
    void createRole_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + lecturerToken)
                        .contentType("application/json")
                        .content("{\"name\":\"Department Head\",\"kind\":\"LECTURER\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- TC-P2-015: PATCH /roles/{id}/permissions ----

    @Test
    void updateRolePermission_asAdmin_togglesGrant() throws Exception {
        mockMvc.perform(patch("/roles/" + lecturerRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"permissionId\":\"" + viewCoursesPermission.getId() + "\",\"granted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0].granted").value(true));
    }

    // ---- G-10: the version field is now a real optimistic-locking check ----

    @Test
    void updateRolePermission_withCurrentVersion_succeeds() throws Exception {
        // lecturerRole was just persisted in @BeforeEach, so its @Version starts at 0.
        mockMvc.perform(patch("/roles/" + lecturerRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"permissionId\":\"" + viewCoursesPermission.getId() + "\",\"granted\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0].granted").value(true));
    }

    @Test
    void updateRolePermission_withStaleVersion_isRejectedWith409() throws Exception {
        mockMvc.perform(patch("/roles/" + lecturerRole.getId() + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"permissionId\":\"" + viewCoursesPermission.getId() + "\",\"granted\":true,\"version\":999}"))
                .andExpect(status().isConflict());
    }

    // ---- TC-P2-016: POST /users/{id}/assignments ----

    @Test
    void createAssignment_global_succeeds() throws Exception {
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"GLOBAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeType").value("GLOBAL"));

        mockMvc.perform(get("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createAssignment_scopedWithoutScopeId_isRejected() throws Exception {
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"DEPARTMENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAssignment_globalWithScopeId_isRejected() throws Exception {
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"GLOBAL\",\"scopeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAssignment_departmentScoped_succeeds() throws Exception {
        UUID departmentId = UUID.randomUUID();
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"DEPARTMENT\",\"scopeId\":\"" + departmentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeType").value("DEPARTMENT"))
                .andExpect(jsonPath("$.scopeId").value(departmentId.toString()));
    }

    @Test
    void createAssignment_asNonAdmin_isRejected() throws Exception {
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + lecturerToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"GLOBAL\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- TC-P2-017/018: overrides + revoke ----

    @Test
    void setOverride_andRevoke_fullLifecycle() throws Exception {
        String createBody = mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"GLOBAL\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createBody);
        String assignmentId = created.get("id").asText();

        mockMvc.perform(patch("/assignments/" + assignmentId + "/overrides")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"permissionId\":\"" + viewCoursesPermission.getId() + "\",\"granted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[0].granted").value(false));

        mockMvc.perform(delete("/assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Scope assignments are served to academic-structure-service, which caches them ----

    @Test
    void internalAssignments_returnTheUsersCurrentAssignments_andChangesDropTheCache() throws Exception {
        UUID departmentId = UUID.randomUUID();
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"DEPARTMENT\",\"scopeId\":\"" + departmentId + "\"}"))
                .andExpect(status().isCreated());
        verify(assignmentCache).evict(lecturerUser.getId());

        mockMvc.perform(get("/internal/assignments/" + lecturerUser.getId())
                        .header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:identity")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scopeType").value("DEPARTMENT"))
                .andExpect(jsonPath("$[0].scopeId").value(departmentId.toString()))
                .andExpect(jsonPath("$[0].roleId").value(lecturerRole.getId().toString()));
    }

    @Test
    void revoke_asNonAdmin_isRejected() throws Exception {
        String createBody = mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"GLOBAL\"}"))
                .andReturn().getResponse().getContentAsString();
        String assignmentId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(delete("/assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isForbidden());
    }
}
