package com.sasimilkservice;

import com.sasimilkservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class SasiMilkServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies Flyway migrations apply and every JPA entity mapping validates
        // against the schema (spring.jpa.hibernate.ddl-auto=validate).
    }
}
