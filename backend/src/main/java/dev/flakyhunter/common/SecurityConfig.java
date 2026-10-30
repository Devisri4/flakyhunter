package dev.flakyhunter.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				// Health and info are public: the website and the keep-alive ping read them without logging in.
				.requestMatchers("/api/actuator/health/**", "/api/actuator/info").permitAll()
				.anyRequest().authenticated());
		return http.build();
	}

}
