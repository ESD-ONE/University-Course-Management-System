package com.ucms.enrollment.config;

import com.ucms.enrollment.security.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Coarse role gates here; ownership is enforced in the service layer — a student acts
 * only for themselves, a lecturer only on offerings they teach (resolved lecturer from
 * academic-structure-service), an admin for anyone. Authorization uses the flat role
 * plus ownership, the same model as identity-access-service and base-service; scope
 * assignments (PermissionResolver) remain academic-structure-service-only (see G-02).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // Spring forwards an error (e.g. a 403) to /error as a separate ERROR
                // dispatch that the JWT filter doesn't run on; without this, that dispatch
                // is anonymous and the client gets 401 instead of the real 403.
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/program-enrollments/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/program-enrollments").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/program-enrollments/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/program-enrollments").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.GET, "/course-enrollments/check", "/stats").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/course-enrollments/my-students").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.GET, "/registrations", "/students").hasAnyRole("ADMIN", "LECTURER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
