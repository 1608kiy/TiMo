package com.timo.words.modules.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timo.words.infrastructure.ai.DeepSeekClient;
import com.timo.words.modules.agent.entity.ConversationQuizLog;
import com.timo.words.modules.agent.repository.ChatMessageRepository;
import com.timo.words.modules.agent.repository.ChatSessionRepository;
import com.timo.words.modules.agent.repository.ConversationQuizLogRepository;
import com.timo.words.modules.calendar.repository.CheckinRecordRepository;
import com.timo.words.modules.examplan.entity.ExamPlan;
import com.timo.words.modules.examplan.repository.ExamPlanRepository;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private DeepSeekClient deepSeekClient;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserWordBindRepository userWordBindRepository;
    @Mock private QuizRecordRepository quizRecordRepository;
    @Mock private CheckinRecordRepository checkinRecordRepository;
    @Mock private ConversationQuizLogRepository conversationQuizLogRepository;
    @Mock private ExamPlanRepository examPlanRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AgentService agentService;

    @Captor private ArgumentCaptor<ConversationQuizLog> logCaptor;

    // --- getRecommendation ---

    @Test
    void testGetRecommendation() {
        // Arrange: user has 3 binds, 1 mastered (stability >= 1.2), 1 due (nextReviewTime in past), 1 error-prone
        UserWordBind mastered = new UserWordBind();
        mastered.setUserId(1L);
        mastered.setWordId(10L);
        mastered.setStability(1.3);
        mastered.setDifficulty(4.0);
        mastered.setConsecutiveErrors(0);
        mastered.setNextReviewTime(LocalDateTime.now().plusDays(5));

        UserWordBind due = new UserWordBind();
        due.setUserId(1L);
        due.setWordId(20L);
        due.setStability(0.8);
        due.setDifficulty(6.0);
        due.setConsecutiveErrors(0);
        due.setNextReviewTime(LocalDateTime.now().minusDays(1));

        UserWordBind errorProne = new UserWordBind();
        errorProne.setUserId(1L);
        errorProne.setWordId(30L);
        errorProne.setStability(0.6);
        errorProne.setDifficulty(8.0);
        errorProne.setConsecutiveErrors(3);
        errorProne.setNextReviewTime(LocalDateTime.now().minusDays(2));

        when(userWordBindRepository.findByUserId(1L)).thenReturn(List.of(mastered, due, errorProne));
        // No conversation-mastered words
        when(conversationQuizLogRepository.findWordIdsUsedCorrectlySince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        AgentService.RecommendResponse resp = agentService.getRecommendation(1L);

        // Assert
        assertNotNull(resp);
        assertTrue(resp.getDailyNewWords() >= 10 && resp.getDailyNewWords() <= 30,
                "Daily new words should be between 10 and 30");
        assertTrue(resp.getDailyReviewWords() >= 20, "Daily review words should be at least 20");
        assertNotNull(resp.getSuggestedMode());
        assertNotNull(resp.getReason());
        assertNotNull(resp.getWeakWords());

        // Error-prone word should appear in weak words
        assertTrue(resp.getWeakWords().stream().anyMatch(w -> w.getWordId().equals(30L)),
                "Error-prone word should be in weak words list");
    }

    @Test
    void testGetRecommendation_manyErrorProne_suggestsContextDeep() {
        // Arrange: create 6 error-prone words to trigger context_deep suggestion
        List<UserWordBind> binds = new java.util.ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            UserWordBind b = new UserWordBind();
            b.setUserId(1L);
            b.setWordId(i);
            b.setStability(0.6);
            b.setDifficulty(7.0);
            b.setConsecutiveErrors(2);
            b.setNextReviewTime(LocalDateTime.now().minusDays(1));
            binds.add(b);
        }

        when(userWordBindRepository.findByUserId(1L)).thenReturn(binds);
        when(conversationQuizLogRepository.findWordIdsUsedCorrectlySince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        AgentService.RecommendResponse resp = agentService.getRecommendation(1L);

        assertEquals("context_deep", resp.getSuggestedMode(),
                "Should suggest context_deep when error-prone count > 5");
        assertTrue(resp.getReason().contains("语境深度学习"),
                "Reason should mention deep learning");
    }

    // --- logConversationQuiz ---

    @Test
    void testLogConversationQuiz() {
        // Arrange
        AgentService.LogQuizRequest req = new AgentService.LogQuizRequest();
        req.setWordId(100L);
        req.setSessionId(50L);
        req.setUsedCorrectly(true);

        // Act
        agentService.logConversationQuiz(1L, req);

        // Assert: verify save was called with correct data
        verify(conversationQuizLogRepository, times(1)).save(logCaptor.capture());
        ConversationQuizLog saved = logCaptor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(100L, saved.getWordId());
        assertEquals(50L, saved.getSessionId());
        assertTrue(saved.getUsedCorrectly());
    }

    // --- checkProgressAlert ---

    @Test
    void testCheckProgressAlert_noPlan_noAlert() {
        // Arrange: no active exam plan
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        // Act
        AgentService.ProgressAlertResponse resp = agentService.checkProgressAlert(1L);

        // Assert
        assertFalse(resp.isHasAlert(), "Should have no alert when no plan exists");
    }

    @Test
    void testCheckProgressAlert_onTrack_noAlert() {
        // Arrange: plan created 10 days ago, estimated 60 days, target 5000 vocab
        // Current vocab = 2000, target = 5000, words to learn = 3000
        // Expected at day 10: 2000 + 3000 * 10/60 = 2500
        // Actual mastered = 3000 (above expected) -> no alert
        ExamPlan plan = new ExamPlan();
        plan.setUserId(1L);
        plan.setTargetVocab(5000);
        plan.setEstimatedDays(60);
        plan.setCreatedAt(LocalDateTime.now().minusDays(10));
        plan.setPlanJson("{\"currentVocab\":2000}");

        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(plan));
        when(userWordBindRepository.countMasteredByUserId(1L)).thenReturn(3000L);

        AgentService.ProgressAlertResponse resp = agentService.checkProgressAlert(1L);

        assertFalse(resp.isHasAlert(), "Should have no alert when on track");
    }

    @Test
    void testCheckProgressAlert_behindSchedule_triggersAlert() {
        // Arrange: plan created 30 days ago, estimated 60 days
        // Current vocab = 0, target = 5000, words to learn = 5000
        // Expected at day 30: 0 + 5000 * 30/60 = 2500
        // Actual mastered = 100 (way behind) -> alert
        ExamPlan plan = new ExamPlan();
        plan.setUserId(1L);
        plan.setTargetVocab(5000);
        plan.setEstimatedDays(60);
        plan.setCreatedAt(LocalDateTime.now().minusDays(30));
        plan.setPlanJson("{\"currentVocab\":0}");

        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(plan));
        when(userWordBindRepository.countMasteredByUserId(1L)).thenReturn(100L);

        AgentService.ProgressAlertResponse resp = agentService.checkProgressAlert(1L);

        assertTrue(resp.isHasAlert(), "Should alert when behind schedule");
        assertTrue(resp.getProgressRatio() < 0.85, "Progress ratio should be below 0.85");
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("落后"), "Message should mention being behind");
        assertNotNull(resp.getSuggestedAction());
        assertEquals(2500.0, resp.getExpectedWords(), "Expected words should be 2500");
        assertEquals(100.0, resp.getActualWords(), "Actual words should be 100");
    }
}
