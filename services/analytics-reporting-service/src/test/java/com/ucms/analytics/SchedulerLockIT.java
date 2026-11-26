package com.ucms.analytics;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With several replicas, a scheduled job must run on one of them only. That is decided
 * by a row in analytics.shedlock, which exists only on the migrated PostgreSQL schema -
 * the "test" profile switches the lock off because H2 has no such table. This checks
 * the table, the migration and SchedulerLockConfig together.
 */
@TestPropertySource(properties = "ucms.scheduler-lock.enabled=true")
class SchedulerLockIT extends AbstractPostgresIT {

    @Autowired LockProvider lockProvider;

    private static LockConfiguration job(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
    }

    @Test
    void aJobHeldByOneInstance_cannotBeTakenByAnother() {
        String name = "it-job-" + UUID.randomUUID();

        Optional<SimpleLock> first = lockProvider.lock(job(name));
        Optional<SimpleLock> second = lockProvider.lock(job(name));

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        first.get().unlock();
    }

    @Test
    void aReleasedJob_canBeTakenAgain() {
        String name = "it-job-" + UUID.randomUUID();
        lockProvider.lock(job(name)).orElseThrow().unlock();

        Optional<SimpleLock> again = lockProvider.lock(job(name));

        assertThat(again).isPresent();
        again.get().unlock();
    }
}
