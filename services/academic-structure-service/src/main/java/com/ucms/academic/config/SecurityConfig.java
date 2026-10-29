package com.ucms.academic.config;

import com.ucms.academic.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Reads are open to any authenticated user (every role needs programs/catalog/course
 * data). Writes are ADMIN-only EXCEPT creating a course offering (POST /courses), which
 * is LECTURER-only, self-service — matches the wireframe's lecturerCreateCourse() exactly
 * (a lecturer creates their own course; there's no lecturer-picker for an admin to assign
 * someone else's course, so this endpoint always sets the caller as the lecturer).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/**").authenticated()
                // Degree Programs / Courses writes are scope-checked in the service
                // layer via PermissionResolver (department-scoped Dept Head grants, a
                // plain Lecturer's self-service creation, or ADMIN) — see
                // docs/SYSTEM_ARCHITECTURE_REPORT.md section 12.3/Phase 3. POST /courses
                // used to be hasRole("LECTURER") only; it's now authenticated() because
                // admins/department heads can also create courses on another lecturer's
                // behalf, which CourseOfferingService enforces explicitly. Everything
                // else (academic years, course catalog) stays coarse ADMIN-only for now.
                // G-19: the gate-level rules in this file are intentionally coarser than
                // actual enforcement for Degree Programs/Courses — anyone auditing
                // security from this file alone should also read PermissionResolver and
                // DegreeProgramService/CourseOfferingService for the real per-endpoint
                // decision (department scoping, self-service lecturer checks, etc.).
                .requestMatchers(HttpMethod.POST, "/courses", "/courses/*/academic-years",
                        "/degree-programs", "/degree-programs/*/curriculum", "/degree-programs/*/academic-years").authenticated()
                // Course/Degree-Program edit & delete are likewise service-layer scope-checked
                // (self-service lecturer, department-scoped Dept Head, or ADMIN) rather than
                // coarse role-gated — see CourseOfferingService/DegreeProgramService. Academic
                // Calendar (years/semesters) writes stay ADMIN-only via anyRequest() below.
                .requestMatchers(HttpMethod.PUT, "/courses/*", "/courses/*/academic-years/*",
                        "/degree-programs/*", "/degree-programs/*/academic-years/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/courses/*", "/courses/*/academic-years/*",
                        "/degree-programs/*", "/degree-programs/*/academic-years/*").authenticated()
                .anyRequest().hasRole("ADMIN")
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
