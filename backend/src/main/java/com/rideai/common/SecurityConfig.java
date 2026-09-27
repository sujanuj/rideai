package com.rideai.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 0: everything is open so we can check the wiring.
 * Phase 1 replaces this with JWT authentication and RIDER / DRIVER roles.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // A stateless JSON API does not use cookies, so CSRF protection is not needed.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/api/ping").permitAll()
                // TODO(Phase 1): change to .anyRequest().authenticated()
                .anyRequest().permitAll());
        return http.build();
    }
}
