package com.ucms.identity;

import com.ucms.identity.domain.Role;
import com.ucms.identity.domain.RoleKind;
import com.ucms.identity.domain.User;
import com.ucms.identity.domain.UserStatus;
import com.ucms.identity.keycloak.KeycloakUserSync;
import com.ucms.identity.repository.RoleRepository;
import com.ucms.identity.repository.UserRepository;
import com.ucms.identity.security.TokenRevocationStore;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sign-in itself happens at Keycloak (its own tests cover wrong passwords, disabled
 * accounts, lockout). This covers what this service decides about the token it's handed:
 * which tokens it trusts, who the caller is, and logout's revocation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @MockBean KeycloakUserSync keycloakUserSync;
    @MockBean TokenRevocationStore revocationStore;

    private User active;

    @BeforeEach
    void seed() {
        Role student = new Role();
        student.setName("Student");
        student.setKind(RoleKind.STUDENT);
        roleRepository.save(student);

        active = new User();
        active.setName("Active Student");
        active.setEmail("active@ucms.local");
        active.setPasswordHash("$2a$10$unused");
        active.setRole(student);
        active.setStatus(UserStatus.ACTIVE);
        active = userRepository.save(active);
    }

    @Test
    void keycloakToken_identifiesTheUcmsUserByItsUidClaim() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + KeycloakTestTokens.user(active.getId(), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("active@ucms.local"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void noToken_isRejected() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().is4xxClientError());
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() throws Exception {
        var otherKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString()).issuer(KeycloakTestTokens.ISSUER)
                .claims(Map.of("azp", "ucms-web", "uid", active.getId().toString(),
                        "realm_access", Map.of("roles", List.of("ADMIN"))))
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(otherKey, Jwts.SIG.RS256).compact();
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        String expired = KeycloakTestTokens.sign(Map.of("azp", "ucms-web", "uid", active.getId().toString(),
                "realm_access", Map.of("roles", List.of("STUDENT"))), Instant.now().minus(5, ChronoUnit.MINUTES));
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void tokenFromAnotherIssuer_isRejected() throws Exception {
        String token = KeycloakTestTokens.sign(Map.of("azp", "ucms-web", "uid", active.getId().toString(),
                        "realm_access", Map.of("roles", List.of("STUDENT"))),
                Instant.now().plus(5, ChronoUnit.MINUTES), "http://elsewhere/realms/ucms");
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void tokenIssuedToAnUnknownClient_isRejected() throws Exception {
        String token = KeycloakTestTokens.sign(Map.of("azp", "admin-cli", "uid", active.getId().toString(),
                "realm_access", Map.of("roles", List.of("STUDENT"))), Instant.now().plus(5, ChronoUnit.MINUTES));
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void userTokenWithoutUid_isRejected() throws Exception {
        String token = KeycloakTestTokens.sign(Map.of("azp", "ucms-web",
                "realm_access", Map.of("roles", List.of("ADMIN"))), Instant.now().plus(5, ChronoUnit.MINUTES));
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void internalEndpoints_acceptOnlyServicesGrantedThisService() throws Exception {
        String path = "/internal/assignments/" + active.getId();
        mockMvc.perform(get(path).header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:identity")))
                .andExpect(status().isOk());
        // A person — even an admin — can't reach them.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + KeycloakTestTokens.user(active.getId(), "ADMIN")))
                .andExpect(status().isForbidden());
        // A service that hasn't been granted svc:identity is not trusted here at all.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:academic")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void logout_putsTheTokenOnTheGatewayRevocationList() throws Exception {
        String token = KeycloakTestTokens.user(active.getId(), "STUDENT");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(revocationStore).revoke(eq(token), any());
    }

    @Test
    void loginEndpoint_isGone() throws Exception {
        mockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"active@ucms.local\",\"password\":\"x\"}"))
                .andExpect(status().is4xxClientError());
    }
}
