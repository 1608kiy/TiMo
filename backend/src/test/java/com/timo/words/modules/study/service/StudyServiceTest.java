package com.timo.words.modules.study.service;

import com.timo.words.algorithm.fsrs.Scheduler;
import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyServiceTest {

    @Mock private UserWordBindRepository userWordBindRepository;
    @Mock private QuizRecordRepository quizRecordRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private StudyService studyService;

    @Captor private ArgumentCaptor<UserWordBind> bindCaptor;
    @Captor private ArgumentCaptor<QuizRecord> recordCaptor;

    private User testUser;
    private UserWordBind existingBind;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setMuInit(8.0);
        testUser.setSigmaInit(3.0);

        existingBind = new UserWordBind();
        existingBind.setId(1L);
        existingBind.setUserId(1L);
        existingBind.setWordId(100L);
        existingBind.setStability(Scheduler.INITIAL_STABILITY);
        existingBind.setDifficulty(Scheduler.INITIAL_DIFFICULTY);
        existingBind.setReviewCount(0);
        existingBind.setConsecutiveErrors(0);
        existingBind.setConsecutiveCorrectSameMode(0);
        existingBind.setLastStudyTime(LocalDateTime.now().minusDays(1));
    }

    // --- submitQuickMemory ---

    @Test
    void testSubmitQuickMemory() {
        // Arrange
        StudyService.QuickMemorySubmitRequest req = new StudyService.QuickMemorySubmitRequest();
        req.setUserId(1L);
        req.setWordId(100L);
        req.setRecognized(true);
        req.setVerifiedCorrect(true);
        req.setReactionTimeMs(3000);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userWordBindRepository.findByUserIdAndWordId(1L, 100L)).thenReturn(Optional.of(existingBind));
        when(quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(1L, 100L, 3.0)).thenReturn(0L);
        when(quizRecordRepository.countByUserIdAndWordId(1L, 100L)).thenReturn(0L);

        // Act
        StudyService.SubmitResponse resp = studyService.submitQuickMemory(req);

        // Assert: response structure
        assertNotNull(resp);
        assertEquals(4.0, resp.getGrade(), 0.001, "Perfect recognition + verification should yield grade 4.0");
        assertTrue(resp.getNewStability() > 0, "New stability should be positive");
        assertTrue(resp.getNewDifficulty() > 0, "New difficulty should be positive");
        assertEquals(1.0, resp.getNewRetrievability(), 0.001, "Post-review retrievability should be 1.0");
        assertTrue(resp.getDf() > 0, "DF should be positive");
        assertFalse(resp.isNeedsSpelling(), "Quick memory should not need spelling");
        assertNotNull(resp.getNextReviewTime(), "Next review time should be set");

        // Assert: bind was saved with updated FSRS values
        verify(userWordBindRepository, times(1)).save(bindCaptor.capture());
        UserWordBind savedBind = bindCaptor.getValue();
        assertTrue(savedBind.getReviewCount() >= 1, "Review count should be incremented");
        assertNotNull(savedBind.getLastStudyTime(), "Last study time should be set");

        // Assert: quiz record was saved
        verify(quizRecordRepository, times(1)).save(recordCaptor.capture());
        QuizRecord savedRecord = recordCaptor.getValue();
        assertEquals(1L, savedRecord.getUserId());
        assertEquals(100L, savedRecord.getWordId());
        assertEquals("quick_memory", savedRecord.getStudyMode());
        assertEquals(4.0, savedRecord.getGrade(), 0.001);
        assertEquals(3000, savedRecord.getReactionTimeMs());
    }

    @Test
    void testSubmitQuickMemory_notRecognized_lowGrade() {
        // Arrange: user does not recognize the word
        StudyService.QuickMemorySubmitRequest req = new StudyService.QuickMemorySubmitRequest();
        req.setUserId(1L);
        req.setWordId(100L);
        req.setRecognized(false);
        req.setVerifiedCorrect(false);
        req.setReactionTimeMs(8000);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userWordBindRepository.findByUserIdAndWordId(1L, 100L)).thenReturn(Optional.of(existingBind));
        when(quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(1L, 100L, 3.0)).thenReturn(0L);
        when(quizRecordRepository.countByUserIdAndWordId(1L, 100L)).thenReturn(0L);

        // Act
        StudyService.SubmitResponse resp = studyService.submitQuickMemory(req);

        // Assert
        assertEquals(1.0, resp.getGrade(), 0.001, "Not recognized should yield grade 1.0");
        assertTrue(resp.getNewStability() <= Scheduler.INITIAL_STABILITY,
                "Stability should decrease or stay same after failure");
    }

    @Test
    void testSubmitQuickMemory_3ConsecutiveErrors_suggestsDeepLearning() {
        // Arrange: bind has 2 consecutive errors already, this will be the 3rd failure
        existingBind.setConsecutiveErrors(2);

        StudyService.QuickMemorySubmitRequest req = new StudyService.QuickMemorySubmitRequest();
        req.setUserId(1L);
        req.setWordId(100L);
        req.setRecognized(true);
        req.setVerifiedCorrect(false); // wrong verification -> grade 2.0 (failure)
        req.setReactionTimeMs(5000);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userWordBindRepository.findByUserIdAndWordId(1L, 100L)).thenReturn(Optional.of(existingBind));
        when(quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(1L, 100L, 3.0)).thenReturn(0L);
        when(quizRecordRepository.countByUserIdAndWordId(1L, 100L)).thenReturn(2L);

        // Act
        StudyService.SubmitResponse resp = studyService.submitQuickMemory(req);

        // Assert: ErrorReinforcementHandler should increment consecutiveErrors
        verify(userWordBindRepository, times(1)).save(bindCaptor.capture());
        UserWordBind savedBind = bindCaptor.getValue();
        assertTrue(savedBind.getConsecutiveErrors() >= 3,
                "Consecutive errors should be >= 3 after this failure");
        assertTrue(resp.isSuggestDeepLearning(),
                "Should suggest deep learning when consecutiveErrors >= 3");
    }

    // --- submitContextDeepGroup ---

    @Test
    void testSubmitContextDeepGroup() {
        // Arrange: a group of 2 words
        StudyService.ContextDeepGroupItem item1 = new StudyService.ContextDeepGroupItem();
        item1.setWordId(100L);
        item1.setS2Raw(4);
        item1.setS3Raw(3);
        item1.setS4Raw(4);
        item1.setS5Raw(3);
        item1.setHintTotal(0);
        item1.setDwellTimeMs(5000);

        StudyService.ContextDeepGroupItem item2 = new StudyService.ContextDeepGroupItem();
        item2.setWordId(200L);
        item2.setS2Raw(2);
        item2.setS3Raw(2);
        item2.setS4Raw(3);
        item2.setS5Raw(1);
        item2.setHintTotal(3);
        item2.setDwellTimeMs(8000);

        StudyService.ContextDeepGroupSubmitRequest req = new StudyService.ContextDeepGroupSubmitRequest();
        req.setUserId(1L);
        req.setGroupResults(List.of(item1, item2));

        // Bind for word 100
        UserWordBind bind100 = new UserWordBind();
        bind100.setId(1L);
        bind100.setUserId(1L);
        bind100.setWordId(100L);
        bind100.setStability(Scheduler.INITIAL_STABILITY);
        bind100.setDifficulty(Scheduler.INITIAL_DIFFICULTY);
        bind100.setReviewCount(0);
        bind100.setConsecutiveErrors(0);
        bind100.setConsecutiveCorrectSameMode(0);
        bind100.setLastStudyTime(LocalDateTime.now().minusDays(1));

        // Bind for word 200
        UserWordBind bind200 = new UserWordBind();
        bind200.setId(2L);
        bind200.setUserId(1L);
        bind200.setWordId(200L);
        bind200.setStability(Scheduler.INITIAL_STABILITY);
        bind200.setDifficulty(Scheduler.INITIAL_DIFFICULTY);
        bind200.setReviewCount(0);
        bind200.setConsecutiveErrors(0);
        bind200.setConsecutiveCorrectSameMode(0);
        bind200.setLastStudyTime(LocalDateTime.now().minusDays(1));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userWordBindRepository.findByUserIdAndWordIdIn(eq(1L), anyCollection())).thenReturn(List.of(bind100, bind200));
        when(quizRecordRepository.countByUserIdAndWordIdIn(eq(1L), anyCollection())).thenReturn(List.of());
        when(quizRecordRepository.countByUserIdAndWordIdInAndGradeGte(eq(1L), anyCollection(), eq(3.0))).thenReturn(List.of());

        // Act
        studyService.submitContextDeepGroup(req);

        // Assert: batch save all binds + 2 quiz records saved
        verify(userWordBindRepository, times(1)).saveAll(anyCollection());
        verify(quizRecordRepository, times(2)).save(any(QuizRecord.class));
    }

    @Test
    void testSubmitContextDeepGroup_nullResults_noop() {
        // Arrange: group results is null
        StudyService.ContextDeepGroupSubmitRequest req = new StudyService.ContextDeepGroupSubmitRequest();
        req.setUserId(1L);
        req.setGroupResults(null);

        // Act
        studyService.submitContextDeepGroup(req);

        // Assert: no interactions
        verify(userWordBindRepository, never()).save(any());
        verify(quizRecordRepository, never()).save(any());
    }
}
