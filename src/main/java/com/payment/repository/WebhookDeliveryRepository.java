package com.payment.repository;

import com.payment.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
}
