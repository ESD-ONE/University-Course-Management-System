package com.ucms.analytics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Snapshot recompute and scheduled-report runs. Off in tests so they're deterministic. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ucms.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
