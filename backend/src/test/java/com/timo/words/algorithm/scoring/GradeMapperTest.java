package com.timo.words.algorithm.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GradeMapperTest {

    // --- Quick Memory Tests ---

    @Test
    void quickMemory_recognizedAndCorrect_returns4() {
        assertEquals(4.0, GradeMapper.mapQuickMemory(true, true));
    }

    @Test
    void quickMemory_recognizedButWrong_returns2() {
        assertEquals(2.0, GradeMapper.mapQuickMemory(true, false));
    }

    @Test
    void quickMemory_notRecognized_returns1() {
        assertEquals(1.0, GradeMapper.mapQuickMemory(false, false));
    }

    @Test
    void quickMemory_notRecognized_verifiedIgnored_returns1() {
        assertEquals(1.0, GradeMapper.mapQuickMemory(false, true));
    }

    // --- Context Deep Tests ---

    @Test
    void contextDeep_allPerfect_returns4() {
        // s2=s3=s4=s5=4 → composite=4.0, penalty=1.0, result=4.0
        assertEquals(4.0, GradeMapper.mapContextDeep(4, 4, 4, 4));
    }

    @Test
    void contextDeep_allZero_returnsClampedToMin() {
        // composite=1.0, penalty=1.0-0.2*(1-0)=0.8, result=0.8 → clamped to 1.0
        assertEquals(1.0, GradeMapper.mapContextDeep(0, 0, 0, 0));
    }

    @Test
    void contextDeep_strongSpellingLowOthers() {
        // s5=4 (good spelling → no penalty), s2=0,s3=0,s4=0
        // composite = 1.0 + 3*(0+0+0+0.25*4/4) = 1.0 + 0.75 = 1.75
        // penalty = 1.0 - 0.2*(1-1) = 1.0
        // result = 1.75
        double result = GradeMapper.mapContextDeep(0, 0, 0, 4);
        assertEquals(1.75, result, 0.001);
    }

    @Test
    void contextDeep_weakSpellingPenalizes() {
        // s5=0 → penalty = 1.0 - 0.2*(1-0) = 0.8
        // s2=s3=s4=4, s5=0
        // composite = 1.0 + 3*(0.20 + 0.25 + 0.30 + 0) = 1.0 + 2.25*3 = wait
        // composite = 1.0 + 3*(0.20*4/4 + 0.25*4/4 + 0.30*4/4 + 0.25*0/4)
        //          = 1.0 + 3*(0.20 + 0.25 + 0.30 + 0) = 1.0 + 3*0.75 = 1.0 + 2.25 = 3.25
        // penalty = 0.8
        // result = 3.25 * 0.8 = 2.6
        double result = GradeMapper.mapContextDeep(4, 4, 4, 0);
        assertEquals(2.6, result, 0.001);
    }

    // --- Unified Review Tests ---

    @Test
    void unifiedReview_allPerfect_returns4() {
        assertEquals(4.0, GradeMapper.mapUnifiedReview(5, 5, 5), 0.001);
    }

    @Test
    void unifiedReview_allZero_returns1() {
        // grade = 1.0 + 3*(0) = 1.0
        assertEquals(1.0, GradeMapper.mapUnifiedReview(0, 0, 0));
    }

    @Test
    void unifiedReview_midScores() {
        // step1=3, step2=3, step3=3
        // grade = 1.0 + 3*(0.35*3/5 + 0.30*3/5 + 0.35*3/5)
        //       = 1.0 + 3*(0.21 + 0.18 + 0.21)
        //       = 1.0 + 3*0.60 = 1.0 + 1.8 = 2.8
        double result = GradeMapper.mapUnifiedReview(3, 3, 3);
        assertEquals(2.8, result, 0.001);
    }
}
