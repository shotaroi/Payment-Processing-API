package com.payment.dto;

import com.payment.domain.PaymentEvent;
import com.payment.domain.PaymentEventType;

import java.time.Instant;

public record PaymentEventResponse(
        Long id,
        PaymentEventType type,
        String payload,
        Instant createdAt
) {
    public static PaymentEventResponse from(PaymentEvent event) {
        return new PaymentEventResponse(
                event.getId(),
                event.getType(),
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}
