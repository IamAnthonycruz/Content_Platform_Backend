package com.pm.content_platform_backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.auth.refresh-token-ttl}")
    private Duration ttl;

    private static final String KEY_PREFIX = "refresh_token:";

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        String key = KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, userId.toString(), ttl);
        return token;
    }

    public Optional<Long> lookUp(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if(value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }
    public void revoke(String token) {
        redisTemplate.delete(KEY_PREFIX+token);

    }
}