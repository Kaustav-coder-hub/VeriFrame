package com.mediaprovenance.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties appProperties;

    public SecurityConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/**", "/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .addFilterBefore(new ApiKeyAuthenticationFilter(appProperties), UsernamePasswordAuthenticationFilter.class);

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
            String method = request.getMethod();

            if (HttpMethod.POST.name().equalsIgnoreCase(method) && path.startsWith("/api/v1/media")) {
                String expectedKey = appProperties.getSecurity().getApiKey();
                String headerName = appProperties.getSecurity().getHeaderName();
                String providedKey = request.getHeader(headerName);

                if (providedKey == null || !providedKey.equals(expectedKey)) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {
                            "type": "https://mediaprovenance.com/errors/unauthorized",
                            "title": "Unauthorized",
                            "status": 401,
                            "detail": "Missing or invalid API key header '%s'"
                        }
                        """.formatted(headerName));
                    return;
                }
            }

            filterChain.doFilter(request, response);
        }
    }
}
