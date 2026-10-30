package dev.flakyhunter;

import org.springframework.boot.SpringApplication;

public class TestFlakyHunterApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlakyHunterApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
