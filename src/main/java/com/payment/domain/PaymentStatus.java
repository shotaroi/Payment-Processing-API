package com.payment.domain;

public enum PaymentStatus {
    CREATED,
    REQUIRES_CONFIRMATION,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
