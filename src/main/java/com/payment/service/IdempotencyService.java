package com.payment.service;

import com.payment.domain.IdempotencyRecord;
import com.payment.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    public Optional<IdempotencyRecord> findCreateRecord(Long merchantId, String idempotencyKey) {
        return idempotencyRecordRepository.findByMerchantIdAndIdempotencyKeyAndOperation(
                merchantId, idempotencyKey, "CREATE");
    }

    public Optional<IdempotencyRecord> findConfirmRecord(Long merchantId, String idempotencyKey, UUID paymentIntentId) {
        return idempotencyRecordRepository.findByMerchantIdAndIdempotencyKeyAndOperationAndPaymentIntentId(
                merchantId, idempotencyKey, "CONFIRM", paymentIntentId);
    }

    @Transactional
    public IdempotencyRecord storeCreate(Long merchantId, String idempotencyKey, String payloadHash, UUID paymentIntentId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setMerchantId(merchantId);
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation("CREATE");
        record.setPayloadHash(payloadHash);
        record.setPaymentIntentId(paymentIntentId);
        return idempotencyRecordRepository.save(record);
    }

    @Transactional
    public IdempotencyRecord storeConfirm(Long merchantId, String idempotencyKey, UUID paymentIntentId, String payloadHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setMerchantId(merchantId);
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation("CONFIRM");
        record.setPaymentIntentId(paymentIntentId);
        record.setPayloadHash(payloadHash);
        return idempotencyRecordRepository.save(record);
    }

    public String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
