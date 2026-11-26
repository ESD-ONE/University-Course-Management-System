package com.ucms.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base for integration tests that need the database production uses: the same
 * PostgreSQL image, provisioned by the same infra/init-schemas.sh, connected as this
 * service's own schema-scoped role. One container is started for the whole test run of
 * this module and shared by every subclass.
 */
@SpringBootTest
@ActiveProfiles({"test", "it"})
abstract class AbstractPostgresIT {

    private static final String SERVICE_ROLE = "notification_svc";
    private static final String SERVICE_PASSWORD = "it-only";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.19-alpine")
            .withDatabaseName("ucms")
            .withUsername("ucms_admin")
            .withPassword("it-only-admin")
            .withEnv("NOTIFICATION_SVC_PASSWORD", SERVICE_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../../infra/init-schemas.sh"),
                    "/docker-entrypoint-initdb.d/init-schemas.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void connectAsTheServiceRole(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> SERVICE_ROLE);
        registry.add("spring.datasource.password", () -> SERVICE_PASSWORD);
    }
}
