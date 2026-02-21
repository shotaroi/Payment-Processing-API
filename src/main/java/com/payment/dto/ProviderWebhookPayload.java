package com.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderWebhookPayload(
        @NotBlank(message = "providerPaymentId is required")
        String providerPaymentId,

        @NotNull(message = "status is required")
        String status,

        String failureCode,

        String failureMessage
) {
    public boolean isSucceeded() {
        return "SUCCEEDED".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }
}
