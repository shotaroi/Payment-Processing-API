package com.payment.dto;

import com.payment.domain.ApiKey;
import com.payment.domain.ApiKeyStatus;

import java.time.Instant;

public record ApiKeyResponse(
        Long id,
        String keyPrefix,
        ApiKeyStatus status,
        Instant createdAt
) {
    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getKeyPrefix() + "****",
                apiKey.getStatus(),
                apiKey.getCreatedAt()
        );
    }
}
