package com.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.domain.IdempotencyRecord;
import com.payment.domain.PaymentIntent;
import com.payment.domain.PaymentStatus;
import com.payment.dto.*;
import com.payment.exception.IdempotencyConflictException;
import com.payment.repository.PaymentIntentRepository;
import com.payment.security.MerchantContext;
import com.payment.service.IdempotencyService;
import com.payment.service.PaymentIntentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment_intents")
@Tag(name = "Payment Intents", description = "Create and manage payment intents")
@SecurityRequirement(name = "ApiKey")
public class PaymentIntentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PaymentIntentService paymentIntentService;
    private final IdempotencyService idempotencyService;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ObjectMapper objectMapper;

    public PaymentIntentController(PaymentIntentService paymentIntentService,
                                   IdempotencyService idempotencyService,
                                   PaymentIntentRepository paymentIntentRepository,
                                   ObjectMapper objectMapper) {
        this.paymentIntentService = paymentIntentService;
        this.idempotencyService = idempotencyService;
        this.paymentIntentRepository = paymentIntentRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a payment intent")
    public PaymentIntentResponse create(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        Long merchantId = getMerchantId();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String payloadHash = idempotencyService.hashPayload(toJson(request));
            Optional<IdempotencyRecord> existing = idempotencyService.findCreateRecord(merchantId, idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getPayloadHash().equals(payloadHash)) {
                    throw new IdempotencyConflictException(
                            "Idempotency key already used with different request payload");
                }
                PaymentIntent original = paymentIntentRepository.findById(existing.get().getPaymentIntentId())
                        .orElseThrow();
                return PaymentIntentResponse.from(original);
            }

            PaymentIntent intent = paymentIntentService.create(
                    merchantId,
                    request.amount(),
                    request.currency(),
                    request.description(),
                    request.customerReference(),
                    idempotencyKey,
                    payloadHash
            );
            return PaymentIntentResponse.from(intent);
        }

        PaymentIntent intent = paymentIntentService.create(
                merchantId,
                request.amount(),
                request.currency(),
                request.description(),
                request.customerReference(),
                null,
                null
        );
        return PaymentIntentResponse.from(intent);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a payment intent")
    public PaymentIntentResponse confirm(
            @PathVariable UUID id,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        Long merchantId = getMerchantId();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required for confirm");
        }

        String payloadHash = idempotencyService.hashPayload(toJson(request));
        Optional<IdempotencyRecord> existing = idempotencyService.findConfirmRecord(merchantId, idempotencyKey, id);
        if (existing.isPresent()) {
            if (!existing.get().getPayloadHash().equals(payloadHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with different request payload");
            }
            PaymentIntent original = paymentIntentRepository.findById(id).orElseThrow();
            return PaymentIntentResponse.from(original);
        }

        PaymentIntent intent = paymentIntentService.confirm(merchantId, id, idempotencyKey, payloadHash);
        return PaymentIntentResponse.from(intent);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a payment intent")
    public PaymentIntentResponse cancel(@PathVariable UUID id) {
        Long merchantId = getMerchantId();
        PaymentIntent intent = paymentIntentService.cancel(merchantId, id);
        return PaymentIntentResponse.from(intent);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment intent by ID")
    public PaymentIntentResponse get(@PathVariable UUID id) {
        Long merchantId = getMerchantId();
        PaymentIntent intent = paymentIntentService.getById(merchantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));
        return PaymentIntentResponse.from(intent);
    }

    @GetMapping
    @Operation(summary = "List payment intents with filters")
    public Page<PaymentIntentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long merchantId = getMerchantId();
        Pageable pageable = PageRequest.of(page, size);
        return paymentIntentService.list(merchantId, status, from, to, pageable)
                .map(PaymentIntentResponse::from);
    }

    private Long getMerchantId() {
        Long id = MerchantContext.getMerchantId();
        if (id != null) return id;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long principal) {
            return principal;
        }
        throw new IllegalStateException("Merchant not authenticated");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }
}
