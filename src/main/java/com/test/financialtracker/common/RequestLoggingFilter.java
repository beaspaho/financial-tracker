package com.test.financialtracker.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single outermost filter. Populates MDC with:
 *   requestId, httpMethod, requestUri, clientIp  — from the raw request
 *   userId, username                             — decoded directly from the
 *                                                  Bearer JWT payload (no
 *                                                  signature check; safe for
 *                                                  logging only)
 * Clears the entire MDC on exit.
 */
@Component
@Order(Integer.MIN_VALUE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        String requestId = Optional
                .ofNullable(request.getHeader("X-Request-ID"))
                .filter(id -> !id.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put("requestId",  requestId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("requestUri", request.getRequestURI());
        MDC.put("clientIp",   resolveClientIp(request));

        // ── user identity from JWT (decoded, not verified — for logging only) ─
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            Map<String, Object> claims = decodeJwtPayload(bearer.substring(7));
            if (claims != null) {
                Object userId   = claims.get("app_user_id");
                Object username = claims.get("preferred_username");
                if (userId   != null) MDC.put("userId",   userId.toString());
                if (username != null) MDC.put("username", username.toString());
            }
        }

        response.setHeader("X-Request-ID", requestId);
        log.debug("Incoming {} {}", request.getMethod(), request.getRequestURI());

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            MDC.put("statusCode", String.valueOf(response.getStatus()));
            MDC.put("durationMs", String.valueOf(durationMs));
            log.info("Completed {} {} -> {} ({}ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMs);
            MDC.clear();
        }
    }


    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            return MAPPER.readValue(new String(decoded, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String padBase64(String s) {
        return s + "=".repeat((4 - s.length() % 4) % 4);
    }
}