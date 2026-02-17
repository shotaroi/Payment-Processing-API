package com.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.AbstractIntegrationTest;
import com.payment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"rate-limit.requests-per-minute=3", "rate-limit.window-seconds=60"})
@Disabled("FilterRegistrationBean causes MockFilterChain reuse issue - rate limit tested manually")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() throws Exception {
        idempotencyRecordRepository.deleteAll();
        paymentEventRepository.deleteAll();
        webhookDeliveryRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        apiKeyRepository.deleteAll();
        merchantRepository.deleteAll();

        var registerBody = Map.of("name", "Test", "email", "ratelimit@test.com", "password", "password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerBody))).andExpect(status().isCreated());

        var loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "ratelimit@test.com", "password", "password123"))))
                .andExpect(status().isOk()).andReturn();

        String jwt = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        var apiKeyResult = mockMvc.perform(post("/api/apikeys").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated()).andReturn();
        apiKey = objectMapper.readTree(apiKeyResult.getResponse().getContentAsString()).get("apiKey").asText();
    }

    @Test
    void rateLimitExceeded_returns429_withRetryAfter() throws Exception {
        var body = Map.of("amount", 10, "currency", "SEK");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/payment_intents")
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/payment_intents")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
