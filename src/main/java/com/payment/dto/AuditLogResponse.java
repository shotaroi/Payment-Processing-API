package com.payment.dto;

import com.payment.domain.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorMerchantId,
        String action,
        String details,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorMerchantId(),
                log.getAction(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
