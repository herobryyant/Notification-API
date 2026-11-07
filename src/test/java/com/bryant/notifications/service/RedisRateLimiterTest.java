package com.bryant.notifications.service;

import com.bryant.notifications.service.NotificationExceptions.RateLimitExceeded;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {
    @Test
    void permitsRequestsUpToConfiguredLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), org.mockito.ArgumentMatchers.<java.util.List<String>>any(),
                any(Object[].class))).thenReturn(5L);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, 5, 60);

        assertDoesNotThrow(() -> limiter.check("person@example.com"));
    }

    @Test
    void rejectsTheRequestAfterConfiguredLimitAndIncludesRetryDelay() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), org.mockito.ArgumentMatchers.<java.util.List<String>>any(),
                any(Object[].class))).thenReturn(6L);
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(37L);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, 5, 60);

        RateLimitExceeded error = assertThrows(RateLimitExceeded.class,
                () -> limiter.check("person@example.com"));

        assertEquals(37L, error.getRetryAfterSeconds());
    }
}
