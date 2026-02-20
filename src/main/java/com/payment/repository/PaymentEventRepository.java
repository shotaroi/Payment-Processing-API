package com.payment.repository;

import com.payment.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findByPaymentIntentIdOrderByCreatedAtAsc(UUID paymentIntentId);
}
