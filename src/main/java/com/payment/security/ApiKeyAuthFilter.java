package com.payment.security;

import com.payment.domain.ApiKey;
import com.payment.domain.ApiKeyStatus;
import com.payment.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-KEY";

    private final ApiKeyRepository apiKeyRepository;
    private final int prefixLength;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository,
                            @Value("${api-key.prefix-length:8}") int prefixLength) {
        this.apiKeyRepository = apiKeyRepository;
        this.prefixLength = prefixLength;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }
            String rawKey = request.getHeader(API_KEY_HEADER);
            if (StringUtils.hasText(rawKey)) {
                String prefix = extractPrefix(rawKey);
                Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyPrefixAndStatus(prefix, ApiKeyStatus.ACTIVE);
                if (apiKeyOpt.isPresent() && passwordEncoder.matches(rawKey, apiKeyOpt.get().getKeyHash())) {
                    Long merchantId = apiKeyOpt.get().getMerchantId();
                    MerchantContext.setMerchantId(merchantId);

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            merchantId, null, Collections.emptyList());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception e) {
            log.debug("API key authentication failed: {}", e.getMessage());
        } finally {
            filterChain.doFilter(request, response);
            MerchantContext.clear();
        }
    }

    private String extractPrefix(String rawKey) {
        return rawKey.length() >= prefixLength ? rawKey.substring(0, prefixLength) : rawKey;
    }
}
