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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired
    PaymentEventRepository paymentEventRepository;
    @Autowired
    WebhookDeliveryRepository webhookDeliveryRepository;
    @Autowired
    PaymentIntentRepository paymentIntentRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    MerchantRepository merchantRepository;

    private String apiKey;
    private String idempotencyKey;

    @BeforeEach
    void setUp() throws Exception {
        idempotencyKey = java.util.UUID.randomUUID().toString();
        idempotencyRecordRepository.deleteAll();
        paymentEventRepository.deleteAll();
        webhookDeliveryRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        apiKeyRepository.deleteAll();
        merchantRepository.deleteAll();

        var registerBody = Map.of("name", "Test", "email", "idem@test.com", "password", "password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerBody))).andExpect(status().isCreated());

        var loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "idem@test.com", "password", "password123"))))
                .andExpect(status().isOk()).andReturn();

        String jwt = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        var apiKeyResult = mockMvc.perform(post("/api/apikeys").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated()).andReturn();
        apiKey = objectMapper.readTree(apiKeyResult.getResponse().getContentAsString()).get("apiKey").asText();
    }

    @Test
    void sameIdempotencyKeySamePayload_returnsSameResponse() throws Exception {
        var body = Map.of("amount", 99.99, "currency", "EUR", "description", "Same");

        var result1 = mockMvc.perform(post("/api/payment_intents")
                .header("X-API-KEY", apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String id1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("id").asText();

        var result2 = mockMvc.perform(post("/api/payment_intents")
                .header("X-API-KEY", apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String id2 = objectMapper.readTree(result2.getResponse().getContentAsString()).get("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals(id1, id2);
    }

    @Test
    void sameIdempotencyKeyDifferentPayload_returns409() throws Exception {
        var body1 = Map.of("amount", 100, "currency", "SEK");
        mockMvc.perform(post("/api/payment_intents")
                .header("X-API-KEY", apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isCreated());

        var body2 = Map.of("amount", 200, "currency", "SEK");
        mockMvc.perform(post("/api/payment_intents")
                .header("X-API-KEY", apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isConflict());
    }
}
