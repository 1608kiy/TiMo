package com.timo.words.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;

    private static final String KEY_PREFIX = "token:blacklist:";

    public void blacklist(String token) {
        long ttlMs = jwtUtil.getRemainingTtl(token);
        if (ttlMs <= 0) return;
        stringRedisTemplate.opsForValue().set(
                KEY_PREFIX + token, "1", ttlMs, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + token));
    }
}
