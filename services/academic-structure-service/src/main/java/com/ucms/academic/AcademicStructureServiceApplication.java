package com.ucms.academic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AcademicStructureServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AcademicStructureServiceApplication.class, args);
    }
}
