package com.timo.words.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_withData() {
        Result<String> result = Result.success("hello");
        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
    }

    @Test
    void success_withMessageAndData() {
        Result<Integer> result = Result.success("ok", 42);
        assertEquals(200, result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals(42, result.getData());
    }

    @Test
    void error_withCodeAndMessage() {
        Result<?> result = Result.error(500, "fail");
        assertEquals(500, result.getCode());
        assertEquals("fail", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void error_withMessage() {
        Result<?> result = Result.error("oops");
        assertEquals(500, result.getCode());
        assertEquals("oops", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void success_nullData() {
        Result<Object> result = Result.success(null);
        assertEquals(200, result.getCode());
        assertNull(result.getData());
    }
}
