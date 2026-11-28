package com.ucms.academic.contract;

import com.ucms.academic.security.AssignmentCache;
import com.ucms.academic.security.AssignmentClaim;
import com.ucms.academic.security.ClientCredentialsTokenProvider;
import com.ucms.academic.security.RoleCatalogClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Runs the real RoleCatalogClient and AssignmentCache against a stub of identity-access-service. The stub is built from that
 * service's own contract files, which its build also checks against its real controllers.
 * So if it renames or drops a field this client reads, one of the two sides fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureStubRunner(
        ids = "com.ucms:identity-access-service",
        repositoryRoot = "stubs://file://${user.dir}/../identity-access-service/src/test/resources/contracts/academic-structure-service",
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        generateStubs = true)
class IdentityContractTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ROLE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PERMISSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired RoleCatalogClient roleCatalog;
    @Autowired AssignmentCache assignmentCache;
    @MockBean ClientCredentialsTokenProvider serviceTokens;
    @MockBean StringRedisTemplate redis;
    @BeforeEach
    void serviceToken() {
        when(serviceTokens.token()).thenReturn("service-token");
    }

    @Test
    void roleCatalog_readsEachRolesPermissionGrants() {
        roleCatalog.refresh();

        assertThat(roleCatalog.baselineGrant(ROLE_ID, "Courses", "View")).isTrue();
        assertThat(roleCatalog.permissionIdOf("Courses", "View")).isEqualTo(PERMISSION_ID);
    }

    @Test
    void assignmentCache_readsAUsersScopedRoles() {
        assertThat(assignmentCache.forUser(USER_ID)).containsExactly(new AssignmentClaim(ASSIGNMENT_ID, ROLE_ID,
                "DEPARTMENT", DEPARTMENT_ID, Map.of(PERMISSION_ID, false)));
    }
}
