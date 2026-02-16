package com.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentIntentRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters (e.g. SEK, EUR)")
        String currency,

        @Size(max = 500)
        String description,

        @Size(max = 255)
        String customerReference
) {
}
