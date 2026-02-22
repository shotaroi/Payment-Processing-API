package com.payment.domain;

public enum PaymentEventType {
    INTENT_CREATED,
    CONFIRM_REQUESTED,
    PROVIDER_AUTHORIZED,
    PROVIDER_CAPTURED,
    SUCCEEDED,
    FAILED,
    CANCELED
}
