package com.classhub.security;

import com.classhub.academicclass.ClassMembershipAccessService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public SecurityConfig(
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            @Value("${server.servlet.session.cookie.secure:false}") boolean cookieSecure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String cookieSameSite) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @Bean
    ActiveClassMembershipFilter activeClassMembershipFilter(
            ClassMembershipAccessService membershipAccessService,
            tools.jackson.databind.ObjectMapper objectMapper) {
        return new ActiveClassMembershipFilter(membershipAccessService, objectMapper);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ActiveClassMembershipFilter activeClassMembershipFilter,
            SessionRegistry sessionRegistry) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .sameSite(cookieSameSite)
                .secure(cookieSecure));

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event ->
                                event.getResponse().sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health", "/ready", "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/forgot-password/verify",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/class-rep/register",
                                "/api/v1/auth/class-rep/setup-link/reissue",
                                "/api/v1/auth/class-rep/complete-account")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/student")
                        .hasAnyRole("STUDENT", "CLASS_REP")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/class-rep")
                        .hasRole("CLASS_REP")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/admin")
                        .hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/class-rep/**").hasRole("CLASS_REP")
                        .requestMatchers(HttpMethod.POST, "/api/v1/course-units")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.POST, "/api/v1/course-units/*/cover-image")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/course-units/*/cover-image")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/course-units/*/status")
                        .hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/course-units/**")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.GET, "/api/v1/course-units", "/api/v1/course-units/**")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/coursework/*/publish",
                                "/api/v1/coursework/*/cancel",
                                "/api/v1/coursework/*/archive")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.POST, "/api/v1/coursework")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.POST, "/api/v1/coursework/*/attachments")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/coursework/*/attachments/**")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/coursework/**")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.GET, "/api/v1/coursework/*/progress")
                        .hasAnyRole("STUDENT", "CLASS_REP")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/coursework/*/progress")
                        .hasAnyRole("STUDENT", "CLASS_REP")
                        .requestMatchers(HttpMethod.GET, "/api/v1/coursework", "/api/v1/coursework/**")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/announcements/*/publish",
                                "/api/v1/announcements/*/archive")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.POST, "/api/v1/announcements")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/announcements/**")
                        .hasAnyRole("SUPER_ADMIN", "CLASS_REP")
                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements", "/api/v1/announcements/**")
                        .authenticated()
                        .requestMatchers("/api/v1/notifications", "/api/v1/notifications/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/semester")
                        .hasAnyRole("STUDENT", "CLASS_REP")
                        .requestMatchers("/api/v1/me/notification-preferences", "/api/v1/me/notification-preferences/**")
                        .hasAnyRole("STUDENT", "CLASS_REP")
                        .requestMatchers("/api/v1/notes", "/api/v1/notes/**")
                        .hasAnyRole("STUDENT", "CLASS_REP")
                        .anyRequest().authenticated())
                .addFilterAfter(activeClassMembershipFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> {
                    headers
                            .contentTypeOptions(Customizer.withDefaults())
                            .frameOptions(frame -> frame.deny())
                            .referrerPolicy(referrer -> referrer.policy(
                                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                            .contentSecurityPolicy(csp -> csp.policyDirectives(
                                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                            .permissionsPolicy(policy -> policy.policy(
                                    "camera=(), microphone=(), geolocation=(), payment=()"));
                    if (cookieSecure) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000));
                    }
                })
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${classhub.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        // Never use "*" with credentials — reject wildcard origins explicitly.
        configuration.setAllowedOrigins(splitOrigins(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static List<String> splitOrigins(String allowedOrigins) {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !"*".equals(origin))
                .toList();
    }
}
