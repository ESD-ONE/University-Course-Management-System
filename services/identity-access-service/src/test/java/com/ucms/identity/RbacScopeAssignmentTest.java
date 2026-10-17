package com.ucms.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.domain.*;
import com.ucms.identity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
    @Autowired com.ucms.identity.security.JwtService jwtService;

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
        userRepository.save(adminUser);

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

        adminToken = login("admin@test.local", "AdminPass1!");
        lecturerToken = login("lecturer@test.local", "LecturerPass1!");
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
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

    // ---- Phase 3 / section 12.3: JWT carries the user's scope assignments ----

    @Test
    void login_embedsAssignmentsClaim() throws Exception {
        mockMvc.perform(post("/users/" + lecturerUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"roleId\":\"" + lecturerRole.getId() + "\",\"scopeType\":\"DEPARTMENT\",\"scopeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());

        String freshToken = login("lecturer@test.local", "LecturerPass1!");
        var claims = jwtService.parse(freshToken);
        var assignments = (java.util.List<?>) claims.get("assignments");
        org.junit.jupiter.api.Assertions.assertFalse(assignments.isEmpty());
        @SuppressWarnings("unchecked")
        var first = (java.util.Map<String, Object>) assignments.get(0);
        org.junit.jupiter.api.Assertions.assertEquals("DEPARTMENT", first.get("scopeType"));
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
