package com.financehub.user_service;

import com.financehub.user_service.config.SecurityConfig;
import com.financehub.user_service.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the User Service microservice.
 * Part of the Personal Finance Hub microservices portfolio project.
 *
 * Spring Security auto-configuration is excluded to allow manual
 * JWT based security configuration via {@link SecurityConfig}.
 *
 * On startup, seeds a default admin user if none exists via
 * {@link UserService#seedAdminUser()}. In production, admin credentials
 * should be rotated after the first human admin account is created.
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class UserServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner seedData(UserService userService) {
		return args -> {
			userService.seedAdminUser();
		};
	}
}