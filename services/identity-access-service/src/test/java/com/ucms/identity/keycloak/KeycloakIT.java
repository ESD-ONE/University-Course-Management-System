package com.ucms.identity.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.KeycloakTestTokens;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User sync and tokens against a real Keycloak that has imported the production realm
 * file (infra/keycloak/realm/ucms-realm.json). Every other test mocks KeycloakUserSync,
 * so this is the only check that the realm - its clients, roles, claim mappers, password
 * policy and the service account's permissions - matches what the code expects.
 *
 * Calls to this service's API are still authorized with test-signed tokens; only the
 * service's own calls to Keycloak are real. The stock Keycloak image is used, so the
 * custom bcrypt provider (migrated accounts) is not covered here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Testcontainers
class KeycloakIT {

    private static final String SECRET = "it-only-secret";
    private static final String IDENTITY_CLIENT = "svc-identity-access";

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0.7")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "it-only-admin")
            .withEnv("KC_HTTP_RELATIVE_PATH", "/auth")
            .withEnv("KC_SVC_IDENTITY_ACCESS_SECRET", SECRET)
            .withEnv("KC_SVC_BASE_SECRET", SECRET)
            .withEnv("KC_SVC_ACADEMIC_STRUCTURE_SECRET", SECRET)
            .withEnv("KC_SVC_ENROLLMENT_SECRET", SECRET)
            .withEnv("KC_SVC_ASSIGNMENT_GRADING_SECRET", SECRET)
            .withEnv("KC_SVC_NOTIFICATION_SECRET", SECRET)
            .withEnv("KC_SVC_ANALYTICS_REPORTING_SECRET", SECRET)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../../infra/keycloak/realm/ucms-realm.json"),
                    "/opt/keycloak/data/import/ucms-realm.json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/auth/realms/ucms/.well-known/openid-configuration")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static String keycloakUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/auth";
    }

    @DynamicPropertySource
    static void useTheRealKeycloak(DynamicPropertyRegistry registry) {
        registry.add("ucms.keycloak.url", KeycloakIT::keycloakUrl);
        registry.add("ucms.keycloak.client-secret", () -> SECRET);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired KeycloakAdminClient keycloak;

    private final RestClient http = RestClient.create();
    private String adminToken;

    private record Account(UUID id, String email, String password) {}

    @BeforeEach
    void seed() {
        Role admin = role("Administrator", RoleKind.ADMIN);
        role("Student", RoleKind.STUDENT);
        role("Lecturer", RoleKind.LECTURER);

        User adminUser = new User();
        adminUser.setName("Admin");
        adminUser.setEmail("admin@test.local");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        adminUser.setRole(admin);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminToken = KeycloakTestTokens.user(userRepository.save(adminUser).getId(), "ADMIN");
    }

    @Test
    void aUserCreatedThroughTheApi_canSignIn_withTheirRole_andIsLinkedByTheirUcmsId() throws Exception {
        Account lecturer = createUser("LECTURER", null);

        JsonNode claims = signIn(lecturer.email(), lecturer.password()).orElseThrow();

        assertThat(realmRoles(claims)).contains("LECTURER");
        // The "ucms_uid" attribute is what the ucms-web client's mapper turns into the
        // "uid" claim. That client only allows the browser login (PKCE), so the claim
        // itself is checked by the end-to-end tests, not here.
        assertThat(keycloak.findByUcmsId(lecturer.id())).isPresent();
    }

    @Test
    void changingThePassword_makesTheOldOneStopWorking() throws Exception {
        Account student = createUser("STUDENT", null);
        String newPassword = "A-Brand-New-Pass-2!";

        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(student.id(), "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + student.password() + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        assertThat(signIn(student.email(), student.password())).isEmpty();
        assertThat(signIn(student.email(), newPassword)).isPresent();
    }

    @Test
    void aPasswordTheRealmPolicyRefuses_isRejected_andNoAccountIsLeftInKeycloak() throws Exception {
        // Long enough for this service's own check; the realm's "notEmail" rule refuses it.
        String email = "policy-" + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P. Policy\",\"email\":\"" + email + "\",\"password\":\"" + email
                                + "\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest());

        assertThat(keycloak.findByUsername(email)).isEmpty();
    }

    @Test
    void aSuspendedUser_cannotSignIn() throws Exception {
        Account student = createUser("STUDENT", null);
        assertThat(signIn(student.email(), student.password())).isPresent();

        mockMvc.perform(patch("/users/" + student.id() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        assertThat(signIn(student.email(), student.password())).isEmpty();
    }

    @Test
    void aDeletedUser_isRemovedFromKeycloak() throws Exception {
        Account student = createUser("STUDENT", null);
        assertThat(keycloak.findByUcmsId(student.id())).isPresent();

        mockMvc.perform(delete("/users/" + student.id()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(keycloak.findByUcmsId(student.id())).isEmpty();
    }

    @Test
    void aServiceAccountToken_carriesTheRolesTheRealmGrantsIt() throws Exception {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "svc-enrollment");
        form.add("client_secret", SECRET);

        JsonNode claims = token(form).orElseThrow();

        assertThat(realmRoles(claims)).contains("service", "svc:academic", "svc:identity");
    }

    private Role role(String name, RoleKind kind) {
        Role role = new Role();
        role.setName(name);
        role.setKind(kind);
        return roleRepository.save(role);
    }

    private Account createUser(String role, String password) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        String body = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"K. Perera\",\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        return new Account(UUID.fromString(created.path("id").asText()), email,
                password != null ? password : created.path("temporaryPassword").asText());
    }

    /** A sign-in the way the service checks a password: the claims if it works, empty if refused. */
    private Optional<JsonNode> signIn(String username, String password) throws Exception {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", IDENTITY_CLIENT);
        form.add("client_secret", SECRET);
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        return token(form);
    }

    private Optional<JsonNode> token(LinkedMultiValueMap<String, String> form) throws Exception {
        String response = http.post()
                .uri(keycloakUrl() + "/realms/ucms/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange((request, reply) -> reply.getStatusCode().is2xxSuccessful()
                        ? new String(reply.getBody().readAllBytes(), StandardCharsets.UTF_8)
                        : null);
        if (response == null) return Optional.empty();
        String accessToken = objectMapper.readTree(response).path("access_token").asText();
        byte[] payload = Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]);
        return Optional.of(objectMapper.readTree(payload));
    }

    private static List<String> realmRoles(JsonNode claims) {
        List<String> roles = new ArrayList<>();
        claims.path("realm_access").path("roles").forEach(r -> roles.add(r.asText()));
        return roles;
    }
}
