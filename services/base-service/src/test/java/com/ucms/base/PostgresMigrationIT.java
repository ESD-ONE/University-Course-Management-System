package com.ucms.base;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unit/API tests run on H2 with Flyway off, so this is where the migrations, the
 * entity mappings and the service role's grants are actually exercised.
 */
class PostgresMigrationIT extends AbstractPostgresIT {

    /** Number of V*.sql files in src/main/resources/db/migration - update with each new one. */
    private static final int MIGRATIONS = 4;

    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsApplyToAnEmptySchema_andTheEntitiesMatchIt() {
        // The context starting at all means Flyway ran and Hibernate's validate passed.
        Integer failed = jdbc.queryForObject(
                "select count(*) from base.flyway_schema_history where not success", Integer.class);
        Integer applied = jdbc.queryForObject(
                "select count(*) from base.flyway_schema_history where success and version is not null",
                Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isEqualTo(MIGRATIONS);
    }

    @Test
    void theServiceRoleCanUseOnlyItsOwnSchema() {
        assertThat(canUseSchema("base")).isTrue();
        assertThat(canUseSchema("identity_access")).isFalse();
        assertThat(canUseSchema("academic")).isFalse();
    }

    private Boolean canUseSchema(String schema) {
        return jdbc.queryForObject("select has_schema_privilege(current_user, ?, 'USAGE')", Boolean.class, schema);
    }
}
