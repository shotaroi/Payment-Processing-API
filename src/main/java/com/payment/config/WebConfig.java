package com.payment.config;

import com.payment.service.RateLimitService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitService rateLimitService,
                                                                   com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(rateLimitService, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/api/payment_intents", "/api/payment_intents/*");
        return registration;
    }
}
