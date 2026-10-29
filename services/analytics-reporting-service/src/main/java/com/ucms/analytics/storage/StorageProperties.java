package com.ucms.analytics.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ucms.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket) {}
