package com.ucms.enrollment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the service against the database production uses: the same PostgreSQL image,
 * provisioned by the same infra/init-schemas.sh, connected as the service's own
 * schema-scoped role. The unit/API tests run on H2 with Flyway off, so this is where the
 * migrations, the entity mappings and the role's grants are actually exercised.
 */
@SpringBootTest
@ActiveProfiles({"test", "it"})
@Testcontainers
class PostgresMigrationIT {

    private static final String SERVICE_ROLE = "enrollment_svc";
    private static final String SERVICE_PASSWORD = "it-only";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.19-alpine")
            .withDatabaseName("ucms")
            .withUsername("ucms_admin")
            .withPassword("it-only-admin")
            .withEnv("ENROLLMENT_SVC_PASSWORD", SERVICE_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../../infra/init-schemas.sh"),
                    "/docker-entrypoint-initdb.d/init-schemas.sh");

    @DynamicPropertySource
    static void connectAsTheServiceRole(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> SERVICE_ROLE);
        registry.add("spring.datasource.password", () -> SERVICE_PASSWORD);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsApplyToAnEmptySchema_andTheEntitiesMatchIt() {
        // The context starting at all means Flyway ran and Hibernate's validate passed.
        Integer failed = jdbc.queryForObject(
                "select count(*) from enrollment.flyway_schema_history where not success", Integer.class);
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'enrollment'", String.class);

        assertThat(failed).isZero();
        assertThat(tables).contains("student_program_enrollments", "course_enrollments", "registration_log");
    }

    @Test
    void theServiceRoleCanUseOnlyItsOwnSchema() {
        assertThat(canUseSchema("enrollment")).isTrue();
        assertThat(canUseSchema("identity_access")).isFalse();
        assertThat(canUseSchema("academic")).isFalse();
    }

    private Boolean canUseSchema(String schema) {
        return jdbc.queryForObject("select has_schema_privilege(current_user, ?, 'USAGE')", Boolean.class, schema);
    }
}
