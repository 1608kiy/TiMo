package com.timo.words.algorithm.fsrs;

public record ReviewResult(
        double newStability,
        double newDifficulty,
        double newRetrievability,
        double nextReviewDays,
        double df,
        boolean stubbornMarked,
        boolean stubbornUnmarked
) {}
