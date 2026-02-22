package com.payment.controller;

import com.payment.dto.ApiKeyResponse;
import com.payment.dto.CreateApiKeyResponse;
import com.payment.security.MerchantContext;
import com.payment.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apikeys")
@Tag(name = "API Keys", description = "Manage API keys for server-to-server calls")
@SecurityRequirement(name = "Bearer")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new API key")
    public CreateApiKeyResponse create() {
        Long merchantId = getMerchantId();
        ApiKeyService.CreateApiKeyResult result = apiKeyService.create(merchantId);
        return new CreateApiKeyResponse(
                result.id(),
                result.rawKey(),
                result.prefix(),
                "Store this key securely. It will not be shown again."
        );
    }

    @GetMapping
    @Operation(summary = "List API keys (masked)")
    public List<ApiKeyResponse> list() {
        Long merchantId = getMerchantId();
        return apiKeyService.listByMerchant(merchantId).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke an API key")
    public void revoke(@PathVariable Long id) {
        Long merchantId = getMerchantId();
        apiKeyService.revoke(id, merchantId);
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
