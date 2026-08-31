package com.sasimilkservice.common.config;

import com.sasimilkservice.common.security.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Supplies the acting user id to {@code @CreatedBy} / {@code @LastModifiedBy}.
 * Empty for anonymous / system operations (startup, Flyway, tests without a
 * security context) — those columns stay null.
 */
@Configuration
public class JpaConfig {

    @Bean
    AuditorAware<Long> auditorAware() {
        return () -> CurrentUser.id();
    }
}
