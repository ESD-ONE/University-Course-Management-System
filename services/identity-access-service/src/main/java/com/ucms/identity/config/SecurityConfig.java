package com.ucms.identity.config;

import com.ucms.identity.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * G-02: this service's authorization is gated purely by the legacy flat {@code role}
 * via {@code hasRole()} below — see every {@code requestMatchers(...)} rule in
 * {@link #filterChain}. The newer {@code role_scope_assignments}/
 * {@code assignment_permission_overrides} data model (RoleScopeAssignment,
 * AssignmentPermissionOverride, AssignmentService/AssignmentController) exists and is
 * fully CRUD-able through this very service's own API, but it is NOT consulted here for
 * any authorization decision. It is only consulted by academic-structure-service's
 * PermissionResolver, for Degree Program / Course endpoints there.
 * <p>
 * This is a deliberate, current-state fact about where the two authorization models
 * actually apply — not a TODO, and not a promise that this will change. An admin who
 * configures a granular scope assignment today gets no effect on this service's own
 * Users/Roles/Permissions endpoints; only on academic-structure-service's
 * Degree-Program/Course endpoints. See docs/gap-analysis-report.md G-02.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Only other backend services (their Keycloak service-account tokens).
                .requestMatchers("/internal/**").hasRole("SERVICE")
                .requestMatchers("/users/me", "/users/me/**").authenticated()
                .requestMatchers("/users/**", "/roles/**", "/permissions/**", "/assignments/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
