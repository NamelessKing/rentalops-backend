package com.rentalops;

import org.springframework.boot.SpringApplication;

public class TestRentalopsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(RentalopsBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
