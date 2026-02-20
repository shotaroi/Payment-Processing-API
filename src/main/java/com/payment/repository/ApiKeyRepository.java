package com.payment.repository;

import com.payment.domain.ApiKey;
import com.payment.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyPrefixAndStatus(String keyPrefix, ApiKeyStatus status);

    List<ApiKey> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    boolean existsByIdAndMerchantId(Long id, Long merchantId);
}
