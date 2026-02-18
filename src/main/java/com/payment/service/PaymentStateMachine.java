package com.payment.service;

import com.payment.domain.PaymentStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment state machine enforcing valid transitions.
 * Rules:
 * - CREATED -> PROCESSING, CANCELED
 * - PROCESSING -> SUCCEEDED, FAILED
 * - SUCCEEDED, FAILED, CANCELED are terminal
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.CANCELED),
            PaymentStatus.REQUIRES_CONFIRMATION, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.CANCELED),
            PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED)
    );

    private static final Set<PaymentStatus> TERMINAL_STATUSES = EnumSet.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.FAILED,
            PaymentStatus.CANCELED
    );

    private PaymentStateMachine() {
    }

    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == to) {
            return true;
        }
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static boolean isTerminal(PaymentStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }

    public static void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Invalid payment transition: " + from + " -> " + to +
                    ". Allowed from " + from + ": " + ALLOWED_TRANSITIONS.get(from));
        }
    }

    public static boolean canCancel(PaymentStatus status) {
        return status == PaymentStatus.CREATED || status == PaymentStatus.REQUIRES_CONFIRMATION;
    }

    public static boolean canConfirm(PaymentStatus status) {
        return status == PaymentStatus.CREATED || status == PaymentStatus.REQUIRES_CONFIRMATION;
    }
}
