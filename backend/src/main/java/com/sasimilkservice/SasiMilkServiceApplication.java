package com.sasimilkservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * SASI MILK SERVICE — modular monolith entry point.
 *
 * <p>One deployable Spring Boot application; module boundaries are enforced by
 * package structure and the {@code spi} interface convention (see
 * {@code docs/ARCHITECTURE.md}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class SasiMilkServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SasiMilkServiceApplication.class, args);
    }
}
