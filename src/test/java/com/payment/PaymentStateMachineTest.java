package com.payment;

import com.payment.domain.PaymentStatus;
import com.payment.service.PaymentStateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStateMachineTest {

    @Test
    void allowedTransitions_fromCreated() {
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.CREATED, PaymentStatus.PROCESSING));
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.CREATED, PaymentStatus.CANCELED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.CREATED, PaymentStatus.SUCCEEDED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.CREATED, PaymentStatus.FAILED));
    }

    @Test
    void allowedTransitions_fromProcessing() {
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.PROCESSING, PaymentStatus.CREATED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.PROCESSING, PaymentStatus.CANCELED));
    }

    @Test
    void terminalStatuses_cannotTransition() {
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.CREATED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.PROCESSING));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.FAILED, PaymentStatus.SUCCEEDED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.CANCELED, PaymentStatus.PROCESSING));
    }

    @Test
    void sameStatus_isAllowed() {
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.CREATED, PaymentStatus.CREATED));
    }

    @Test
    void isTerminal() {
        assertTrue(PaymentStateMachine.isTerminal(PaymentStatus.SUCCEEDED));
        assertTrue(PaymentStateMachine.isTerminal(PaymentStatus.FAILED));
        assertTrue(PaymentStateMachine.isTerminal(PaymentStatus.CANCELED));
        assertFalse(PaymentStateMachine.isTerminal(PaymentStatus.CREATED));
        assertFalse(PaymentStateMachine.isTerminal(PaymentStatus.PROCESSING));
    }

    @Test
    void canCancel() {
        assertTrue(PaymentStateMachine.canCancel(PaymentStatus.CREATED));
        assertTrue(PaymentStateMachine.canCancel(PaymentStatus.REQUIRES_CONFIRMATION));
        assertFalse(PaymentStateMachine.canCancel(PaymentStatus.PROCESSING));
        assertFalse(PaymentStateMachine.canCancel(PaymentStatus.SUCCEEDED));
        assertFalse(PaymentStateMachine.canCancel(PaymentStatus.FAILED));
        assertFalse(PaymentStateMachine.canCancel(PaymentStatus.CANCELED));
    }

    @Test
    void canConfirm() {
        assertTrue(PaymentStateMachine.canConfirm(PaymentStatus.CREATED));
        assertTrue(PaymentStateMachine.canConfirm(PaymentStatus.REQUIRES_CONFIRMATION));
        assertFalse(PaymentStateMachine.canConfirm(PaymentStatus.PROCESSING));
        assertFalse(PaymentStateMachine.canConfirm(PaymentStatus.SUCCEEDED));
    }

    @Test
    void validateTransition_throwsForInvalid() {
        assertThrows(IllegalStateException.class,
                () -> PaymentStateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.SUCCEEDED));
        assertThrows(IllegalStateException.class,
                () -> PaymentStateMachine.validateTransition(PaymentStatus.SUCCEEDED, PaymentStatus.CREATED));
    }

    @Test
    void validateTransition_succeedsForValid() {
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.PROCESSING));
        assertDoesNotThrow(() -> PaymentStateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED));
    }
}
