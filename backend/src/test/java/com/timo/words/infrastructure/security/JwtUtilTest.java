package com.timo.words.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-for-unit-tests-only-32chars!", 604800000L);
    }

    @Test
    void generateToken_returnsNonEmpty() {
        String token = jwtUtil.generateToken(1L, "test@example.com");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUserId_fromToken() {
        String token = jwtUtil.generateToken(42L, "user@test.com");
        assertEquals(42L, jwtUtil.getUserId(token));
    }

    @Test
    void isTokenValid_validToken() {
        String token = jwtUtil.generateToken(1L, "test@example.com");
        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_invalidToken() {
        assertFalse(jwtUtil.isTokenValid("invalid.token.here"));
    }

    @Test
    void isTokenValid_emptyToken() {
        assertFalse(jwtUtil.isTokenValid(""));
    }

    @Test
    void parseToken_containsEmail() {
        String token = jwtUtil.generateToken(1L, "test@example.com");
        var claims = jwtUtil.parseToken(token);
        assertEquals("test@example.com", claims.get("email"));
    }

    @Test
    void differentUsers_differentTokens() {
        String token1 = jwtUtil.generateToken(1L, "a@test.com");
        String token2 = jwtUtil.generateToken(2L, "b@test.com");
        assertNotEquals(token1, token2);
        assertEquals(1L, jwtUtil.getUserId(token1));
        assertEquals(2L, jwtUtil.getUserId(token2));
    }
}
