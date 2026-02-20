package com.payment.repository;

import com.payment.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKeyAndOperation(
            Long merchantId, String idempotencyKey, String operation);

    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKeyAndOperationAndPaymentIntentId(
            Long merchantId, String idempotencyKey, String operation, UUID paymentIntentId);
}
