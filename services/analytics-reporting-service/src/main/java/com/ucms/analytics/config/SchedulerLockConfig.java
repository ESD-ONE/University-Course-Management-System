package com.ucms.analytics.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * With several replicas, each @SchedulerLock job runs on only one of them at a time: the
 * replica that takes the row in analytics.shedlock (V__shedlock.sql) runs it, the others
 * skip that turn. Times come from the database, so replicas' clocks don't need to agree.
 */
@Configuration
@ConditionalOnProperty(name = "ucms.scheduler-lock.enabled", havingValue = "true", matchIfMissing = true)
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("analytics.shedlock")
                .usingDbTime()
                .build());
    }
}
