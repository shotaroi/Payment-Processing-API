package com.payment.dto;

import com.payment.domain.PaymentIntent;
import com.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID id,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String customerReference,
        String providerPaymentId,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentIntentResponse from(PaymentIntent intent) {
        return new PaymentIntentResponse(
                intent.getId(),
                intent.getStatus(),
                intent.getAmount(),
                intent.getCurrency(),
                intent.getDescription(),
                intent.getCustomerReference(),
                intent.getProviderPaymentId(),
                intent.getFailureCode(),
                intent.getFailureMessage(),
                intent.getCreatedAt(),
                intent.getUpdatedAt()
        );
    }
}
