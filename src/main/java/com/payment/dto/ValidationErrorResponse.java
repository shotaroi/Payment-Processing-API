package com.payment.dto;

import java.util.List;

public record ValidationErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ErrorResponse.FieldError> fieldErrors
) {
}
