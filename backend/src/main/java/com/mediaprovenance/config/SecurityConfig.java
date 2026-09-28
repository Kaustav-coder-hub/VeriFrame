package com.mediaprovenance.config;

import com.mediaprovenance.common.CorrelationIdFilter;
import com.mediaprovenance.common.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties appProperties;
    private final Environment environment;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(AppProperties appProperties, Environment environment, RateLimitFilter rateLimitFilter) {
        this.appProperties = appProperties;
        this.environment = environment;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isLocal = environment.acceptsProfiles(Profiles.of("local"));

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                
                if (isLocal) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/h2-console/**").permitAll();
                }

                // Public REST API endpoints for user browser flows
                auth.requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/media", "/api/v1/media/**").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/verify").permitAll();

                // Protected admin/maintenance endpoints
                auth.requestMatchers("/api/v1/admin/**").authenticated();
                auth.anyRequest().permitAll();
            });

        if (isLocal) {
            http.headers(headers -> headers.frameOptions(frame -> frame.disable()));
        }

        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(new ApiKeyAuthenticationFilter(appProperties), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

        private final AppProperties appProperties;

        public ApiKeyAuthenticationFilter(AppProperties appProperties) {
            this.appProperties = appProperties;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String path = request.getRequestURI();

            if (path.startsWith("/api/v1/admin/")) {
                String expectedKey = appProperties.getSecurity().getApiKey();
                String headerName = appProperties.getSecurity().getHeaderName();
                String providedKey = request.getHeader(headerName);

                if (providedKey == null || !constantTimeEquals(providedKey, expectedKey)) {
                    sendUnauthorizedProblemResponse(response, headerName);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "admin-client", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        }

        private boolean constantTimeEquals(String a, String b) {
            if (a == null || b == null) {
                return false;
            }
            byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
            byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(aBytes, bBytes);
        }

        private void sendUnauthorizedProblemResponse(HttpServletResponse response, String headerName) throws IOException {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                {
                    "type": "urn:verimedia:error:unauthorized",
                    "title": "UNAUTHORIZED",
                    "status": 401,
                    "detail": "Missing or invalid API key header '%s'",
                    "correlationId": "%s",
                    "timestamp": "%s"
                }
                """.formatted(headerName, correlationId != null ? correlationId : "", Instant.now().toString()));
        }
    }
}
