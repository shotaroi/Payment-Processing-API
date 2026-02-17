package com.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.AbstractIntegrationTest;
import com.payment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PaymentEventRepository paymentEventRepository;
    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired
    WebhookDeliveryRepository webhookDeliveryRepository;
    @Autowired
    PaymentIntentRepository paymentIntentRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    MerchantRepository merchantRepository;

    private String apiKey;
    private String intentId;

    @BeforeEach
    void setUp() throws Exception {
        idempotencyRecordRepository.deleteAll();
        paymentEventRepository.deleteAll();
        webhookDeliveryRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        apiKeyRepository.deleteAll();
        merchantRepository.deleteAll();

        var registerBody = Map.of("name", "Test", "email", "concur@test.com", "password", "password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerBody))).andExpect(status().isCreated());

        var loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "concur@test.com", "password", "password123"))))
                .andExpect(status().isOk()).andReturn();

        String jwt = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        var apiKeyResult = mockMvc.perform(post("/api/apikeys").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated()).andReturn();
        apiKey = objectMapper.readTree(apiKeyResult.getResponse().getContentAsString()).get("apiKey").asText();

        var createResult = mockMvc.perform(post("/api/payment_intents")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("amount", 50, "currency", "EUR"))))
                .andExpect(status().isCreated()).andReturn();
        intentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void concurrentConfirm_onlyOneSucceeds_noDoubleEvents() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        String idempotencyKey = UUID.randomUUID().toString();
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    var body = Map.of("paymentMethodType", "CARD", "paymentMethodToken", "tok_test_visa");
                    var result = mockMvc.perform(post("/api/payment_intents/" + intentId + "/confirm")
                            .header("X-API-KEY", apiKey)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(50);
        }

        assertEquals(threads, successCount.get(), "All should return 200 (idempotent or first success)");

        var events = paymentEventRepository.findByPaymentIntentIdOrderByCreatedAtAsc(UUID.fromString(intentId));
        long confirmCount = events.stream().filter(e -> e.getType().name().equals("CONFIRM_REQUESTED")).count();
        assertEquals(1, confirmCount);
    }
}
