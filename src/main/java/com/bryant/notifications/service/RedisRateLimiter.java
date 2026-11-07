package com.bryant.notifications.service;

import com.bryant.notifications.service.NotificationExceptions.RateLimitExceeded;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); " +
            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; " +
            "return count;", Long.class);

    private final StringRedisTemplate redis;
    private final int maxPerWindow;
    private final int windowSeconds;

    public RedisRateLimiter(StringRedisTemplate redis,
                            @Value("${app.rate-limit.max-per-window:5}") int maxPerWindow,
                            @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
        this.redis = redis;
        this.maxPerWindow = maxPerWindow;
        this.windowSeconds = windowSeconds;
    }

    public void check(String recipientEmail) {
        String key = "notification-rate:" + sha256(recipientEmail.trim().toLowerCase());
        Long count = redis.execute(INCREMENT_WITH_EXPIRY, List.of(key), Integer.toString(windowSeconds));
        if (count != null && count > maxPerWindow) {
            long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            throw new RateLimitExceeded(Math.max(1, ttl));
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
