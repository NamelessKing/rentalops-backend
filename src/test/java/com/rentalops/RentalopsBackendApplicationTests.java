package com.rentalops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
class RentalopsBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
