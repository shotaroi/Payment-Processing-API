package com.payment.dto;

public record CreateApiKeyResponse(
        Long id,
        String apiKey,
        String keyPrefix,
        String message
) {
}
