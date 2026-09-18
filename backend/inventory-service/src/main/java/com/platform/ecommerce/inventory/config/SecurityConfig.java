package com.platform.ecommerce.inventory.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/api/v1/inventory/hold", "/api/v1/inventory/release",
                        "/api/v1/inventory/bulk-lock", "/api/v1/inventory/bulk-release",
                        "/api/v1/inventory/stream/**").permitAll()
                // ^ these are server-to-server endpoints called directly by order-service over
                // internal DNS (not through the gateway - see InventoryClient's base-url config),
                // so they're not reachable by external clients in the first place. Authorization
                // for the bulk-release decision is enforced one layer up, at order-service's
                // BulkOrderController (@PreAuthorize("hasRole('ADMIN')")), matching the same
                // trust model already used for /hold and /release.
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
