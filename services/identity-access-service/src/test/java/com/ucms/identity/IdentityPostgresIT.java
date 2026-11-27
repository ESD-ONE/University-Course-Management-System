package com.ucms.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.identity.keycloak.KeycloakUserSync;
import com.ucms.identity.repository.PermissionRepository;
import com.ucms.identity.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Rules that depend on PostgreSQL itself: the student ID sequence and row-level
 * conflicts between two admins. Runs on the migrated schema with its seed data, so the
 * seeded administrator is the caller. Not @Transactional: each request commits on its
 * own connection. Every test uses fresh, uniquely named data in a throwaway database.
 */
@AutoConfigureMockMvc
class IdentityPostgresIT extends AbstractPostgresIT {

    /** Seed Administrator, V2__seed_data.sql. */
    private static final UUID SEED_ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PermissionRepository permissions;
    @Autowired RolePermissionRepository rolePermissions;
    @MockBean KeycloakUserSync keycloakUserSync;

    private final String adminToken = KeycloakTestTokens.user(SEED_ADMIN, "ADMIN");

    private record Response(int status, String body) {}

    @Test
    void studentsCreatedAtTheSameTime_getDistinctStudentIds() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        List<Callable<Response>> requests = IntStream.range(0, 20)
                .<Callable<Response>>mapToObj(i -> () -> send(post("/users")
                        .content("{\"name\":\"Student " + i + "\",\"email\":\"it-" + run + "-" + i
                                + "@test.local\",\"role\":\"STUDENT\"}")))
                .toList();

        List<Response> responses = atTheSameTime(requests);

        assertThat(responses).allMatch(r -> r.status() == 201);
        List<String> studentIds = new ArrayList<>();
        for (Response r : responses) studentIds.add(objectMapper.readTree(r.body()).path("studentId").asText());
        assertThat(studentIds).allMatch(id -> id.matches("STU-\\d{4}-\\d{4}"));
        assertThat(studentIds).doesNotHaveDuplicates();
    }

    /**
     * Regression test for a defect this test found: both requests saw no existing grant
     * and both inserted one; the second hit the role_permissions primary key and the
     * caller got a 500. Fixed in RoleService.updateRolePermission on the branch
     * fix/role-permission-concurrent-grant - this test fails until that fix is merged.
     */
    @Test
    void twoAdminsGrantingTheSamePermissionAtOnce_neitherGetsAServerError_andOneGrantIsStored() throws Exception {
        JsonNode role = objectMapper.readTree(send(post("/roles")
                .content("{\"name\":\"IT role " + UUID.randomUUID() + "\",\"kind\":\"LECTURER\"}")).body());
        UUID roleId = UUID.fromString(role.path("id").asText());
        UUID permissionId = permissions.findAllByOrderByModuleAscActionKeyAsc().get(0).getId();
        Callable<Response> grant = () -> send(patch("/roles/" + roleId + "/permissions")
                .content("{\"permissionId\":\"" + permissionId + "\",\"granted\":true}"));

        List<Response> responses = atTheSameTime(List.of(grant, grant));

        // The loser of the race may be told to retry (409), but must never get a 500.
        assertThat(responses).allMatch(r -> r.status() == 200 || r.status() == 409);
        assertThat(responses).anyMatch(r -> r.status() == 200);
        assertThat(rolePermissions.findByRoleId(roleId)).hasSize(1);
    }

    private Response send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request.header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")).andReturn();
        return new Response(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    /** Runs every request on its own thread, all released at the same instant. */
    private static List<Response> atTheSameTime(List<Callable<Response>> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<Response> request : requests) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) responses.add(future.get(60, TimeUnit.SECONDS));
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
