package com.timo.words.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void constructor_withResultCode() {
        BusinessException ex = new BusinessException(ResultCode.USER_NOT_FOUND);
        assertEquals(1001, ex.getCode());
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void constructor_withCodeAndMessage() {
        BusinessException ex = new BusinessException(400, "bad request");
        assertEquals(400, ex.getCode());
        assertEquals("bad request", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        BusinessException ex = new BusinessException(ResultCode.INTERNAL_ERROR);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void allResultCodes_haveUniqueCodes() {
        ResultCode[] codes = ResultCode.values();
        long uniqueCount = java.util.Arrays.stream(codes)
                .mapToInt(ResultCode::getCode)
                .distinct().count();
        assertEquals(codes.length, uniqueCount, "All ResultCodes should have unique code values");
    }
}
