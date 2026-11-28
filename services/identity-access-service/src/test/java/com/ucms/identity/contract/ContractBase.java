package com.ucms.identity.contract;

import com.ucms.identity.KeycloakTestTokens;
import com.ucms.identity.keycloak.KeycloakUserSync;
import com.ucms.identity.service.AssignmentService;
import com.ucms.identity.service.RoleService;
import com.ucms.identity.service.UserService;
import com.ucms.identity.web.dto.AssignmentClaim;
import com.ucms.identity.web.dto.ProfileResponse;
import com.ucms.identity.web.dto.RolePermissionGrant;
import com.ucms.identity.web.dto.RoleResponse;
import com.ucms.identity.web.dto.UserResponse;
import com.ucms.identity.web.dto.UserSummaryResponse;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base class of the tests generated from src/test/resources/contracts (one per file).
 * Each contract is what another service expects from this one, so a generated test fails
 * when a response here stops matching what a caller reads.
 *
 * The real controllers, JSON mapping and security run. The services behind them are
 * mocked and return the fixed answers below, because a contract is about the shape of a
 * response; the behaviour behind it is covered by the other tests of this service.
 * Requests carry a service token, as calls from other services do.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ContractBase {

    static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID UNKNOWN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    static final UUID ROLE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID PERMISSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    static final UUID ASSIGNMENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired WebApplicationContext context;
    @MockBean KeycloakUserSync keycloakUserSync;
    @MockBean UserService users;
    @MockBean RoleService roles;
    @MockBean AssignmentService assignments;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:identity")))
                .build());

        UserResponse user = new UserResponse(USER_ID, "K. Perera", "k.perera@uni.edu", "STUDENT", "ACTIVE",
                "STU-2025-0001", ROLE_ID, "Student");
        when(users.getUser(USER_ID)).thenReturn(user);
        when(users.getUser(UNKNOWN_ID)).thenThrow(new EntityNotFoundException("User not found"));
        when(users.lookup(any())).thenReturn(List.of(user));
        when(users.search(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(user)));
        when(users.summary()).thenReturn(new UserSummaryResponse(120, 110, 100, 20));
        when(users.getProfile(any())).thenReturn(new ProfileResponse(USER_ID, "K. Perera", "k.perera@uni.edu",
                "STUDENT", "ACTIVE", "STU-2025-0001", true, true, false, true, List.of()));

        when(roles.listRoles()).thenReturn(List.of(new RoleResponse(ROLE_ID, "Department Head", "LECTURER",
                List.of(new RolePermissionGrant(PERMISSION_ID, "Courses", "View", true)))));

        when(assignments.resolveClaimsForUser(USER_ID)).thenReturn(List.of(new AssignmentClaim(ASSIGNMENT_ID, ROLE_ID,
                "DEPARTMENT", DEPARTMENT_ID, Map.of(PERMISSION_ID, false))));
    }
}
