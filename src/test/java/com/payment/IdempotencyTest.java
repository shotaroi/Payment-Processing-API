package com.payment;

import com.payment.repository.IdempotencyRecordRepository;
import com.payment.service.IdempotencyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyTest {

    private final IdempotencyService idempotencyService = new IdempotencyService(
            org.mockito.Mockito.mock(IdempotencyRecordRepository.class));

    @Test
    void samePayload_producesSameHash() {
        String payload1 = "{\"amount\":100,\"currency\":\"SEK\"}";
        String payload2 = "{\"amount\":100,\"currency\":\"SEK\"}";
        assertEquals(idempotencyService.hashPayload(payload1), idempotencyService.hashPayload(payload2));
    }

    @Test
    void differentPayload_producesDifferentHash() {
        String payload1 = "{\"amount\":100,\"currency\":\"SEK\"}";
        String payload2 = "{\"amount\":200,\"currency\":\"SEK\"}";
        assertNotEquals(idempotencyService.hashPayload(payload1), idempotencyService.hashPayload(payload2));
    }

    @Test
    void hashPayload_isDeterministic() {
        String payload = "{\"amount\":100,\"currency\":\"EUR\",\"description\":\"test\"}";
        String hash1 = idempotencyService.hashPayload(payload);
        String hash2 = idempotencyService.hashPayload(payload);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }
}
