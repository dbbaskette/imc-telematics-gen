package com.acme.insurance.telematics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TelematicsGeneratorApplicationTest {

    @Test
    void contextLoads() {
        // Test that the Spring Boot application context loads successfully
    }
}