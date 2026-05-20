package com.timo.words.algorithm.common;

public final class ClampUtil {

    private ClampUtil() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(Double value, double min, double max) {
        if (value == null) return min;
        return clamp(value.doubleValue(), min, max);
    }
}
