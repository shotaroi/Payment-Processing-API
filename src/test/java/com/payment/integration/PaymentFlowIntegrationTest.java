package com.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.AbstractIntegrationTest;
import com.payment.domain.PaymentStatus;
import com.payment.repository.ApiKeyRepository;
import com.payment.repository.IdempotencyRecordRepository;
import com.payment.repository.MerchantRepository;
import com.payment.repository.PaymentEventRepository;
import com.payment.repository.PaymentIntentRepository;
import com.payment.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MerchantRepository merchantRepository;
    @Autowired
    PaymentIntentRepository paymentIntentRepository;
    @Autowired
    PaymentEventRepository paymentEventRepository;
    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired
    WebhookDeliveryRepository webhookDeliveryRepository;

    private String jwtToken;
    private String apiKey;
    private Long merchantId;

    @BeforeEach
    void setUp() throws Exception {
        idempotencyRecordRepository.deleteAll();
        paymentEventRepository.deleteAll();
        webhookDeliveryRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        apiKeyRepository.deleteAll();
        merchantRepository.deleteAll();

        var registerBody = Map.of(
                "name", "Test Merchant",
                "email", "merchant@test.com",
                "password", "password123"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated());

        var loginBody = Map.of("email", "merchant@test.com", "password", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();
        jwtToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        var merchant = merchantRepository.findByEmail("merchant@test.com").orElseThrow();
        merchantId = merchant.getId();

        MvcResult apiKeyResult = mockMvc.perform(post("/api/apikeys")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isCreated())
                .andReturn();
        apiKey = objectMapper.readTree(apiKeyResult.getResponse().getContentAsString()).get("apiKey").asText();
    }

    @Test
    void createIntent_confirm_webhookSuccess_statusSucceeded_andEventsRecorded() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        var createBody = Map.of(
                "amount", 100.50,
                "currency", "SEK",
                "description", "Test payment"
        );

        MvcResult createResult = mockMvc.perform(post("/api/payment_intents")
                        .header("X-API-KEY", apiKey)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value(100.50))
                .andExpect(jsonPath("$.currency").value("SEK"))
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var confirmBody = Map.of(
                "paymentMethodType", "CARD",
                "paymentMethodToken", "tok_test_visa"
        );
        String confirmIdempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/payment_intents/" + intentId + "/confirm")
                        .header("X-API-KEY", apiKey)
                        .header("Idempotency-Key", confirmIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerPaymentId").exists())
                .andReturn();

        var events = paymentEventRepository.findByPaymentIntentIdOrderByCreatedAtAsc(UUID.fromString(intentId));
        assertEquals(3, events.size());
        assertEquals("INTENT_CREATED", events.get(0).getType().name());
        assertEquals("CONFIRM_REQUESTED", events.get(1).getType().name());
        assertEquals("SUCCEEDED", events.get(2).getType().name());
    }

    @Test
    void createIntent_thenCancel_statusCanceled() throws Exception {
        var createBody = Map.of("amount", 50, "currency", "EUR");

        MvcResult createResult = mockMvc.perform(post("/api/payment_intents")
                        .header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/payment_intents/" + intentId + "/cancel")
                        .header("X-API-KEY", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }
}
