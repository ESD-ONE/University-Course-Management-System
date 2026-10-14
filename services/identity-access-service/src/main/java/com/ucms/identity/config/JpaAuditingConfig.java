package com.ucms.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Spring Data's default DateTimeProvider for @CreatedDate/@LastModifiedDate produces
 * java.time.LocalDateTime, which Hibernate cannot coerce into our timestamptz
 * (OffsetDateTime) audit columns — it fails at flush time with
 * "Cannot convert unsupported date type java.time.LocalDateTime to java.time.OffsetDateTime".
 * Supplying OffsetDateTime directly here matches the DATA_GOVERNANCE.md requirement that
 * every audit timestamp be timezone-aware.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
