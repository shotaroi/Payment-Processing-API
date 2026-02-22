package com.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.dto.ErrorResponse;
import com.payment.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            String rateLimitKey = apiKey.length() >= 8 ? apiKey.substring(0, 8) : apiKey;
            int retryAfter = rateLimitService.checkAndIncrement(rateLimitKey);
            if (retryAfter > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorResponse error = new ErrorResponse(
                        Instant.now().toString(),
                        429,
                        "Too Many Requests",
                        "Rate limit exceeded. Retry after " + retryAfter + " seconds.",
                        request.getRequestURI(),
                        null
                );
                response.getWriter().write(objectMapper.writeValueAsString(error));
                response.getWriter().flush();
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/payment_intents");
    }
}
