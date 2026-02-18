package com.payment.service;

import com.payment.domain.ApiKey;
import com.payment.domain.ApiKeyStatus;
import com.payment.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final ApiKeyRepository apiKeyRepository;
    private final AuditService auditService;
    private final int prefixLength;
    private final int keyLength;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         AuditService auditService,
                         @Value("${api-key.prefix-length:8}") int prefixLength,
                         @Value("${api-key.key-length:32}") int keyLength) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditService = auditService;
        this.prefixLength = prefixLength;
        this.keyLength = keyLength;
    }

    @Transactional
    public CreateApiKeyResult create(Long merchantId) {
        byte[] bytes = new byte[keyLength];
        RANDOM.nextBytes(bytes);
        String rawKey = "pk_" + BASE64.encodeToString(bytes);
        String prefix = rawKey.substring(0, Math.min(prefixLength, rawKey.length()));

        ApiKey apiKey = new ApiKey();
        apiKey.setMerchantId(merchantId);
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(passwordEncoder.encode(rawKey));
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey = apiKeyRepository.save(apiKey);

        log.info("API key created: id={}, merchantId={}, prefix={}", apiKey.getId(), merchantId, prefix);
        auditService.log(merchantId, "API_KEY_CREATED", "apiKeyId=" + apiKey.getId());

        return new CreateApiKeyResult(apiKey.getId(), rawKey, prefix);
    }

    public List<ApiKey> listByMerchant(Long merchantId) {
        return apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional
    public void revoke(Long apiKeyId, Long merchantId) {
        if (!apiKeyRepository.existsByIdAndMerchantId(apiKeyId, merchantId)) {
            throw new IllegalArgumentException("API key not found");
        }
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKeyRepository.save(apiKey);
        log.info("API key revoked: id={}, merchantId={}", apiKeyId, merchantId);
        auditService.log(merchantId, "API_KEY_REVOKED", "apiKeyId=" + apiKeyId);
    }

    public record CreateApiKeyResult(Long id, String rawKey, String prefix) {
    }
}
