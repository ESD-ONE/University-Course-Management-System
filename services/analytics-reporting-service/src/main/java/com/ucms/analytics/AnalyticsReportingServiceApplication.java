package com.ucms.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AnalyticsReportingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsReportingServiceApplication.class, args);
    }
}
