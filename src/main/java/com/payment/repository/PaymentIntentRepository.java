package com.payment.repository;

import com.payment.domain.PaymentIntent;
import com.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByIdAndMerchantId(UUID id, Long merchantId);

    Optional<PaymentIntent> findByMerchantIdAndIdempotencyKeyCreate(Long merchantId, String idempotencyKey);

    @Query("SELECT p FROM PaymentIntent p WHERE p.merchantId = :merchantId " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:from IS NULL OR p.createdAt >= :from) " +
           "AND (:to IS NULL OR p.createdAt <= :to)")
    Page<PaymentIntent> findByMerchantIdAndFilters(
            @Param("merchantId") Long merchantId,
            @Param("status") PaymentStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    Optional<PaymentIntent> findByProviderPaymentId(String providerPaymentId);
}
