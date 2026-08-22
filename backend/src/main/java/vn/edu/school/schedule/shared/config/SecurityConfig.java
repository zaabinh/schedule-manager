package vn.edu.school.schedule.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.edu.school.schedule.shared.security.AuthOriginFilter;
import vn.edu.school.schedule.shared.security.SessionAuthenticationFilter;
import vn.edu.school.schedule.shared.security.SessionCsrfFilter;
import java.util.List;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter,
                                            SessionCsrfFilter csrfFilter, AuthOriginFilter originFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/departments/**", "/api/v1/business-roles/**",
                                "/api/v1/classes/**", "/api/v1/organization/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/academic-years/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/weeks/*/plan").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/weekly-plans/current").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/weekly-plans/published").authenticated()
                        .requestMatchers("/api/v1/dashboard/admin").hasRole("ADMIN")
                        .requestMatchers("/api/v1/dashboard/me").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tasks/*/attachments").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/task-attachments/*/download").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/task-attachments/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tasks/me").hasRole("USER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/tasks/*/complete").hasRole("USER")
                        .requestMatchers("/api/v1/tasks/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/reminders/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/reminders").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/conversations").hasRole("USER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/conversations/*/close").hasRole("ADMIN")
                        .requestMatchers("/api/v1/conversations/**").authenticated()
                        .requestMatchers("/api/v1/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/weekly-plans/*/export").hasRole("ADMIN")
                        .requestMatchers("/api/v1/academic-years/**", "/api/v1/weeks/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/weekly-plans/**", "/api/v1/events/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                vn.edu.school.schedule.shared.security.SecurityErrorWriter.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) ->
                                vn.edu.school.schedule.shared.security.SecurityErrorWriter.forbidden(response)))
                .addFilterBefore(originFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(csrfFilter, AuthorizationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.security.allowed-origins}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "X-Correlation-Id", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-CSRF-Token", "X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
