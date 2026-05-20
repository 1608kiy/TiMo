package com.timo.words.algorithm.fsrs;

import com.timo.words.modules.study.entity.UserWordBind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorReinforcementHandlerTest {

    private UserWordBind bind;

    @BeforeEach
    void setUp() {
        bind = new UserWordBind();
        bind.setUserId(1L);
        bind.setWordId(100L);
        bind.setStability(1.0);
        bind.setDifficulty(5.0);
        bind.setConsecutiveErrors(0);
        bind.setIsStubborn(false);
        bind.setConsecutiveCorrectSameMode(0);
        bind.setLastStudyMode(null);
    }

    // --- Quick Memory ---

    @Test
    void quickMemory_firstFailure_mildPenalty() {
        double origS = bind.getStability();
        double origD = bind.getDifficulty();

        ErrorReinforcementHandler.apply(bind, "quick_memory", 2.0, null, 0, true, false);

        assertEquals(origS * 0.9, bind.getStability(), 0.001);
        assertEquals(origD + 0.05, bind.getDifficulty(), 0.001);
        assertFalse(bind.getIsStubborn());
    }

    @Test
    void quickMemory_secondConsecutiveFailure_marksStubborn() {
        bind.setConsecutiveErrors(1);
        double origS = bind.getStability();

        ErrorReinforcementHandler.apply(bind, "quick_memory", 1.0, null, 0, true, false);

        assertEquals(origS * 0.8, bind.getStability(), 0.001);
        assertTrue(bind.getIsStubborn());
    }

    @Test
    void quickMemory_success_noPenalty() {
        double origS = bind.getStability();
        double origD = bind.getDifficulty();

        ErrorReinforcementHandler.apply(bind, "quick_memory", 4.0, null, 0, true, false);

        assertEquals(origS, bind.getStability(), 0.001);
        assertEquals(origD, bind.getDifficulty(), 0.001);
    }

    // --- Context Deep ---

    @Test
    void contextDeep_spellingDifficulty_penalizesStability() {
        // s5 <= 1
        double origS = bind.getStability();
        ErrorReinforcementHandler.apply(bind, "context_deep", 2.0,
                new int[]{3, 3, 3, 1}, 2, true, false);

        assertTrue(bind.getStability() < origS);
    }

    @Test
    void contextDeep_heavyHints_penalizesStability() {
        // hintTotal >= 4
        double origS = bind.getStability();
        ErrorReinforcementHandler.apply(bind, "context_deep", 3.0,
                new int[]{4, 4, 4, 4}, 4, true, false);

        assertTrue(bind.getStability() < origS);
    }

    @Test
    void contextDeep_consecutiveErrors_marksStubborn() {
        bind.setConsecutiveErrors(1);
        ErrorReinforcementHandler.apply(bind, "context_deep", 1.5,
                new int[]{2, 2, 2, 2}, 2, true, false);

        assertTrue(bind.getIsStubborn());
    }

    // --- Unified Review ---

    @Test
    void unifiedReview_failureGrade_penalizes() {
        double origS = bind.getStability();
        ErrorReinforcementHandler.apply(bind, "unified_review", 1.5,
                null, 0, true, false);

        assertTrue(bind.getStability() < origS);
    }

    @Test
    void unifiedReview_spellingWrong_marksStubborn() {
        ErrorReinforcementHandler.apply(bind, "unified_review", 3.0,
                null, 0, false, true);

        assertTrue(bind.getIsStubborn());
        assertTrue(bind.getStability() < 1.0);
    }

    // --- Stubborn Unmarking ---

    @Test
    void stubbornUnmark_afterThreeConsecutiveGood() {
        bind.setIsStubborn(true);
        bind.setLastStudyMode("quick_memory");
        bind.setConsecutiveCorrectSameMode(2);

        ErrorReinforcementHandler.apply(bind, "quick_memory", 3.5,
                null, 0, true, false);

        assertFalse(bind.getIsStubborn());
        assertNull(bind.getStubbornSince());
    }

    @Test
    void stubbornUnmark_resetsConsecutiveErrors() {
        bind.setIsStubborn(true);
        bind.setConsecutiveErrors(3);
        bind.setLastStudyMode("quick_memory");
        bind.setConsecutiveCorrectSameMode(2);

        ErrorReinforcementHandler.apply(bind, "quick_memory", 3.5,
                null, 0, true, false);

        assertEquals(0, bind.getConsecutiveErrors());
    }

    @Test
    void stubbornUnmark_contextDeep_immediatelyUnmarks() {
        bind.setIsStubborn(true);
        bind.setLastStudyMode("quick_memory");
        bind.setConsecutiveCorrectSameMode(2);

        ErrorReinforcementHandler.apply(bind, "context_deep", 3.5,
                null, 0, true, false);

        // context_deep with grade >= 3.5 immediately unmarks stubborn
        // (deep learning is designed for stubborn word remediation)
        assertFalse(bind.getIsStubborn());
        // After unmark, the grade>=3.5 branch resets counter to 1 (new mode)
        assertEquals(1, bind.getConsecutiveCorrectSameMode());
    }

    // --- Consecutive Errors Management ---

    @Test
    void consecutiveErrors_incrementsOnFailure() {
        bind.setConsecutiveErrors(0);
        ErrorReinforcementHandler.apply(bind, "quick_memory", 1.5,
                null, 0, true, false);

        assertEquals(1, bind.getConsecutiveErrors());
    }

    @Test
    void consecutiveErrors_resetsOnSuccess() {
        bind.setConsecutiveErrors(3);
        ErrorReinforcementHandler.apply(bind, "quick_memory", 4.0,
                null, 0, true, false);

        assertEquals(0, bind.getConsecutiveErrors());
    }
}
