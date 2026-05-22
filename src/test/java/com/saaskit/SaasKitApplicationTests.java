package com.tuganire;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TuganireApplicationTests {

    @Test
    void contextLoads() {
        // Boots the Spring context with the test profile to catch wiring regressions.
    }
}
