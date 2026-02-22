package com.payment.controller;

import com.payment.domain.AuditLog;
import com.payment.domain.PaymentEvent;
import com.payment.domain.PaymentIntent;
import com.payment.dto.AuditLogResponse;
import com.payment.dto.PaymentEventResponse;
import com.payment.security.MerchantContext;
import com.payment.service.AuditService;
import com.payment.service.PaymentIntentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Admin / Observability", description = "Events and audit logs")
@SecurityRequirement(name = "Bearer")
public class AdminController {

    private final PaymentIntentService paymentIntentService;
    private final AuditService auditService;

    public AdminController(PaymentIntentService paymentIntentService, AuditService auditService) {
        this.paymentIntentService = paymentIntentService;
        this.auditService = auditService;
    }

    @GetMapping("/api/events/payment_intents/{id}")
    @Operation(summary = "List payment events (timeline)")
    public List<PaymentEventResponse> getPaymentEvents(@PathVariable UUID id) {
        Long merchantId = getMerchantId();
        var intentOpt = paymentIntentService.getById(merchantId, id);
        if (intentOpt.isEmpty()) {
            throw new IllegalArgumentException("Payment intent not found");
        }
        return paymentIntentService.getEvents(id).stream()
                .map(PaymentEventResponse::from)
                .toList();
    }

    @GetMapping("/api/admin/audit")
    @Operation(summary = "List audit logs")
    public Page<AuditLogResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditService.list(pageable).map(AuditLogResponse::from);
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
}
