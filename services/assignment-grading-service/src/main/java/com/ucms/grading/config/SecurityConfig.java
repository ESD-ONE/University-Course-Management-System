package com.ucms.grading.config;

import com.ucms.grading.security.JwtAuthFilter;
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
 * Role gates here; ownership in the service layer — a lecturer manages assignments and
 * grades only for offerings they teach (resolved lecturer from academic-structure-service),
 * a student submits and reads only their own work in offerings they're enrolled in.
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
                .requestMatchers(HttpMethod.POST, "/assignments", "/assignments/*/brief").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.PUT, "/assignments/*", "/assignments/*/grades", "/submissions/*/grade").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.PATCH, "/assignments/*/status").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.DELETE, "/assignments/*").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.GET, "/assignments/*/submissions").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.GET, "/grades/offerings/**").hasAnyRole("ADMIN", "LECTURER")
                .requestMatchers(HttpMethod.POST, "/assignments/*/submissions").hasRole("STUDENT")
                .requestMatchers("/stats/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
