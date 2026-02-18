package com.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    private final StringRedisTemplate redisTemplate;
    private final int requestsPerMinute;
    private final int windowSeconds;

    public RateLimitService(StringRedisTemplate redisTemplate,
                            @Value("${rate-limit.requests-per-minute:60}") int requestsPerMinute,
                            @Value("${rate-limit.window-seconds:60}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.requestsPerMinute = requestsPerMinute;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Check and increment rate limit for the given key (e.g. API key prefix).
     * @return remaining seconds until retry if rate limited, or 0 if allowed
     */
    public int checkAndIncrement(String key) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            count = 1L;
        }
        if (count == 1) {
            redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
        }
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        if (ttl == null) {
            ttl = (long) windowSeconds;
        }
        if (count > requestsPerMinute) {
            log.debug("Rate limit exceeded for key: {}, count={}", key, count);
            return ttl != null ? ttl.intValue() : windowSeconds;
        }
        return 0;
    }
}
