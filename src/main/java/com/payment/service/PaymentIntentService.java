package com.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.domain.*;
import com.payment.repository.PaymentEventRepository;
import com.payment.repository.PaymentIntentRepository;
import com.payment.repository.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentIntentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntentService.class);

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private final boolean simulateSuccess;
    private final long simulateTimeoutMs;

    public PaymentIntentService(PaymentIntentRepository paymentIntentRepository,
                                PaymentEventRepository paymentEventRepository,
                                WebhookDeliveryRepository webhookDeliveryRepository,
                                IdempotencyService idempotencyService,
                                AuditService auditService,
                                ObjectMapper objectMapper,
                                @Value("${payment.provider.simulate-success:true}") boolean simulateSuccess,
                                @Value("${payment.provider.simulate-timeout-ms:5000}") long simulateTimeoutMs) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.simulateSuccess = simulateSuccess;
        this.simulateTimeoutMs = simulateTimeoutMs;
    }

    @Transactional
    public PaymentIntent create(Long merchantId, BigDecimal amount, String currency,
                               String description, String customerReference,
                               String idempotencyKey, String payloadHash) {
        PaymentIntent intent = new PaymentIntent();
        intent.setMerchantId(merchantId);
        intent.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        intent.setCurrency(currency);
        intent.setStatus(PaymentStatus.CREATED);
        intent.setDescription(description);
        intent.setCustomerReference(customerReference);
        if (idempotencyKey != null) {
            intent.setIdempotencyKeyCreate(idempotencyKey);
        }
        intent = paymentIntentRepository.save(intent);

        PaymentEvent event = createEvent(intent.getId(), PaymentEventType.INTENT_CREATED, null);
        paymentEventRepository.save(event);

        if (idempotencyKey != null && payloadHash != null) {
            idempotencyService.storeCreate(merchantId, idempotencyKey, payloadHash, intent.getId());
        }

        auditService.log(merchantId, "PAYMENT_INTENT_CREATED", "intentId=" + intent.getId() + ", amount=" + amount + " " + currency);
        log.info("Payment intent created: id={}, merchantId={}, amount={} {}", intent.getId(), merchantId, amount, currency);
        return intent;
    }

    @Transactional
    public PaymentIntent confirm(Long merchantId, UUID intentId, String idempotencyKey, String payloadHash) {
        PaymentIntent intent = paymentIntentRepository.findByIdAndMerchantId(intentId, merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));

        if (!PaymentStateMachine.canConfirm(intent.getStatus())) {
            throw new IllegalStateException("Cannot confirm payment in status: " + intent.getStatus());
        }

        PaymentStateMachine.validateTransition(intent.getStatus(), PaymentStatus.PROCESSING);
        intent.setStatus(PaymentStatus.PROCESSING);

        String providerPaymentId = "pay_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        intent.setProviderPaymentId(providerPaymentId);

        PaymentEvent confirmEvent = createEvent(intent.getId(), PaymentEventType.CONFIRM_REQUESTED,
                "{\"providerPaymentId\":\"" + providerPaymentId + "\"}");
        paymentEventRepository.save(confirmEvent);

        if (idempotencyKey != null && payloadHash != null) {
            idempotencyService.storeConfirm(merchantId, idempotencyKey, intentId, payloadHash);
        }
        intent.setIdempotencyKeyConfirm(idempotencyKey);

        if (simulateSuccess) {
            intent.setStatus(PaymentStatus.SUCCEEDED);
            paymentEventRepository.save(createEvent(intent.getId(), PaymentEventType.SUCCEEDED, null));
        } else {
            intent.setStatus(PaymentStatus.FAILED);
            intent.setFailureCode("provider_error");
            intent.setFailureMessage("Simulated provider failure");
            paymentEventRepository.save(createEvent(intent.getId(), PaymentEventType.FAILED,
                    "{\"failureCode\":\"provider_error\"}"));
        }

        try {
            intent = paymentIntentRepository.save(intent);
        } catch (ObjectOptimisticLockingFailureException e) {
            intent = paymentIntentRepository.findByIdAndMerchantId(intentId, merchantId)
                    .orElseThrow(() -> new IllegalStateException("Payment intent not found after concurrent update"));
            log.info("Confirm idempotent (concurrent): id={}, merchantId={}, status={}", intentId, merchantId, intent.getStatus());
            return intent;
        }
        auditService.log(merchantId, "PAYMENT_CONFIRMED", "intentId=" + intentId + ", status=" + intent.getStatus());
        log.info("Payment confirmed: id={}, merchantId={}, status={}", intentId, merchantId, intent.getStatus());
        return intent;
    }

    @Transactional
    public PaymentIntent cancel(Long merchantId, UUID intentId) {
        PaymentIntent intent = paymentIntentRepository.findByIdAndMerchantId(intentId, merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));

        if (!PaymentStateMachine.canCancel(intent.getStatus())) {
            throw new IllegalStateException("Cannot cancel payment in status: " + intent.getStatus());
        }

        PaymentStateMachine.validateTransition(intent.getStatus(), PaymentStatus.CANCELED);
        intent.setStatus(PaymentStatus.CANCELED);

        paymentEventRepository.save(createEvent(intent.getId(), PaymentEventType.CANCELED, null));
        intent = paymentIntentRepository.save(intent);

        auditService.log(merchantId, "PAYMENT_CANCELED", "intentId=" + intentId);
        log.info("Payment canceled: id={}, merchantId={}", intentId, merchantId);
        return intent;
    }

    @Transactional
    public PaymentIntent handleProviderWebhook(String providerPaymentId, String status, String failureCode, String failureMessage) {
        PaymentIntent intent = paymentIntentRepository.findByProviderPaymentId(providerPaymentId)
                .orElse(null);

        if (intent == null) {
            log.warn("Webhook for unknown providerPaymentId: {}", providerPaymentId);
            return null;
        }

        PaymentStatus targetStatus = "SUCCEEDED".equalsIgnoreCase(status) ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        if (!PaymentStateMachine.canTransition(intent.getStatus(), targetStatus)) {
            log.info("Webhook idempotent: intent {} already in terminal state {}", intent.getId(), intent.getStatus());
            return intent;
        }

        intent.setStatus(targetStatus);
        if (targetStatus == PaymentStatus.FAILED) {
            intent.setFailureCode(failureCode);
            intent.setFailureMessage(failureMessage);
        }

        PaymentEventType eventType = targetStatus == PaymentStatus.SUCCEEDED ? PaymentEventType.SUCCEEDED : PaymentEventType.FAILED;
        paymentEventRepository.save(createEvent(intent.getId(), eventType,
                "{\"providerPaymentId\":\"" + providerPaymentId + "\",\"status\":\"" + status + "\"}"));

        intent = paymentIntentRepository.save(intent);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setPaymentIntentId(intent.getId());
        delivery.setEventType(status);
        delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
        delivery.setAttempts(1);
        delivery.setLastAttemptAt(Instant.now());
        webhookDeliveryRepository.save(delivery);

        auditService.log(intent.getMerchantId(), "WEBHOOK_PROCESSED", "intentId=" + intent.getId() + ", status=" + status);
        log.info("Webhook processed: intentId={}, providerPaymentId={}, status={}", intent.getId(), providerPaymentId, status);
        return intent;
    }

    public Optional<PaymentIntent> getById(Long merchantId, UUID intentId) {
        return paymentIntentRepository.findByIdAndMerchantId(intentId, merchantId);
    }

    public Page<PaymentIntent> list(Long merchantId, PaymentStatus status, Instant from, Instant to, Pageable pageable) {
        return paymentIntentRepository.findByMerchantIdAndFilters(merchantId, status, from, to, pageable);
    }

    public List<PaymentEvent> getEvents(UUID paymentIntentId) {
        return paymentEventRepository.findByPaymentIntentIdOrderByCreatedAtAsc(paymentIntentId);
    }

    public Optional<PaymentIntent> getByIdForEvents(UUID intentId) {
        return paymentIntentRepository.findById(intentId);
    }

    private PaymentEvent createEvent(UUID paymentIntentId, PaymentEventType type, String payload) {
        PaymentEvent event = new PaymentEvent();
        event.setPaymentIntentId(paymentIntentId);
        event.setType(type);
        event.setPayload(payload);
        return event;
    }
}
