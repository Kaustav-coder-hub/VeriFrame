package com.mediaprovenance.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private final Map<String, RateTracker> trackers = new ConcurrentHashMap<>();

    private static class RateTracker {
        final long minuteBucket;
        final AtomicInteger count;

        RateTracker(long minuteBucket) {
            this.minuteBucket = minuteBucket;
            this.count = new AtomicInteger(1);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Apply rate limit to public POST endpoints: /api/v1/media and /api/v1/verify
        if ("POST".equalsIgnoreCase(method) && (path.startsWith("/api/v1/media") || path.startsWith("/api/v1/verify"))) {
            String clientIp = getClientIp(request);
            long currentMinute = System.currentTimeMillis() / 60000;

            RateTracker tracker = trackers.compute(clientIp, (ip, existing) -> {
                if (existing == null || existing.minuteBucket != currentMinute) {
                    return new RateTracker(currentMinute);
                } else {
                    existing.count.incrementAndGet();
                    return existing;
                }
            });

            if (tracker.count.get() > MAX_REQUESTS_PER_MINUTE) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("""
                    {
                        "type": "urn:verimedia:error:rate-limit-exceeded",
                        "title": "TOO_MANY_REQUESTS",
                        "status": 429,
                        "detail": "Rate limit exceeded. Maximum %d requests per minute allowed.",
                        "timestamp": "%s"
                    }
                    """.formatted(MAX_REQUESTS_PER_MINUTE, Instant.now().toString()));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
