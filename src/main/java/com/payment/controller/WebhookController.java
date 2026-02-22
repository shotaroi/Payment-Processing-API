package com.payment.controller;

import com.payment.dto.PaymentIntentResponse;
import com.payment.dto.ProviderWebhookPayload;
import com.payment.service.PaymentIntentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Provider callback simulation")
public class WebhookController {

    private final PaymentIntentService paymentIntentService;

    public WebhookController(PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping("/provider")
    @Operation(summary = "Simulate provider callback (SUCCEEDED/FAILED)")
    public ResponseEntity<PaymentIntentResponse> providerWebhook(@Valid @RequestBody ProviderWebhookPayload payload) {
        var intent = paymentIntentService.handleProviderWebhook(
                payload.providerPaymentId(),
                payload.status(),
                payload.failureCode(),
                payload.failureMessage()
        );
        if (intent == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(PaymentIntentResponse.from(intent));
    }
}
