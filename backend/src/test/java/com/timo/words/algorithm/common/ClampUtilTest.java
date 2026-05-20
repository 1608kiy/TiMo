package com.timo.words.algorithm.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClampUtilTest {

    @Test
    void clamp_withinRange_returnsValue() {
        assertEquals(5.0, ClampUtil.clamp(5.0, 0.0, 10.0));
    }

    @Test
    void clamp_belowMin_returnsMin() {
        assertEquals(0.0, ClampUtil.clamp(-5.0, 0.0, 10.0));
    }

    @Test
    void clamp_aboveMax_returnsMax() {
        assertEquals(10.0, ClampUtil.clamp(15.0, 0.0, 10.0));
    }

    @Test
    void clamp_atMin_returnsMin() {
        assertEquals(0.0, ClampUtil.clamp(0.0, 0.0, 10.0));
    }

    @Test
    void clamp_atMax_returnsMax() {
        assertEquals(10.0, ClampUtil.clamp(10.0, 0.0, 10.0));
    }

    @Test
    void clamp_nullValue_returnsMin() {
        assertEquals(0.0, ClampUtil.clamp((Double) null, 0.0, 10.0));
    }

    @Test
    void clamp_nullValue_differentMin() {
        assertEquals(5.0, ClampUtil.clamp((Double) null, 5.0, 10.0));
    }

    @Test
    void clamp_negativeRange() {
        assertEquals(-3.0, ClampUtil.clamp(-3.0, -10.0, -1.0));
    }

    @Test
    void clamp_sameMinMax() {
        assertEquals(7.0, ClampUtil.clamp(100.0, 7.0, 7.0));
    }
}
