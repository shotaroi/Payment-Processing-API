package com.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(
        @NotBlank(message = "Payment method type is required")
        @Size(max = 32)
        String paymentMethodType,

        @NotBlank(message = "Payment method token is required")
        @Size(max = 255)
        String paymentMethodToken
) {
}
