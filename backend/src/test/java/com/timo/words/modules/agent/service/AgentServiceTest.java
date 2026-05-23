package com.timo.words.modules.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timo.words.infrastructure.ai.DeepSeekClient;
import com.timo.words.modules.agent.entity.ChatMessage;
import com.timo.words.modules.agent.entity.ConversationQuizLog;
import com.timo.words.modules.agent.repository.ChatMessageRepository;
import com.timo.words.modules.agent.repository.ChatSessionRepository;
import com.timo.words.modules.agent.repository.ConversationQuizLogRepository;
import com.timo.words.modules.calendar.repository.CheckinRecordRepository;
import com.timo.words.modules.examplan.entity.ExamPlan;
import com.timo.words.modules.examplan.repository.ExamPlanRepository;
import com.timo.words.modules.word.entity.Example;
import com.timo.words.modules.word.entity.Meaning;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.repository.WordRepository;
import com.timo.words.modules.word.repository.ExampleRepository;
import com.timo.words.modules.word.repository.MeaningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import java.util.Map;
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
    @Mock private WordRepository wordRepository;
    @Mock private MeaningRepository meaningRepository;
    @Mock private ExampleRepository exampleRepository;

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
        when(quizRecordRepository.avgGradeByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(3.0);

        // Act
        AgentService.RecommendResponse resp = agentService.getRecommendation(1L);

        // Assert
        assertNotNull(resp);
        assertEquals(5, resp.getDailyNewWords(), "Cold start should keep new words small");
        assertTrue(resp.getDailyReviewWords() >= 10, "Daily review words should be at least 10");
        assertEquals("unified_review", resp.getSuggestedMode());
        assertTrue(resp.getReason().contains("统一复习"));
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
        when(quizRecordRepository.avgGradeByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(3.5);

        AgentService.RecommendResponse resp = agentService.getRecommendation(1L);

        assertEquals("context_deep", resp.getSuggestedMode(),
                "Should suggest context_deep when error-prone count > 5");
        assertTrue(resp.getReason().contains("语境深度学习"),
                "Reason should mention deep learning");
    }

    @Test
    void testGetRecommendation_coldStart_returnsSmallNewWordCount() {
        List<UserWordBind> binds = new java.util.ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            UserWordBind b = new UserWordBind();
            b.setUserId(1L);
            b.setWordId(i);
            b.setStability(0.8);
            b.setDifficulty(5.0);
            b.setConsecutiveErrors(0);
            b.setNextReviewTime(LocalDateTime.now().plusDays(1));
            binds.add(b);
        }

        when(userWordBindRepository.findByUserId(1L)).thenReturn(binds);
        when(conversationQuizLogRepository.findWordIdsUsedCorrectlySince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(quizRecordRepository.avgGradeByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(3.2);

        AgentService.RecommendResponse resp = agentService.getRecommendation(1L);

        assertTrue(resp.getDailyNewWords() <= 8, "Cold start should keep new words small");
        assertEquals("quick_memory", resp.getSuggestedMode());
    }

    @Test
    void testGetRecommendation_lowAccuracy_reducesNewWords() {
        List<UserWordBind> binds = new java.util.ArrayList<>();
        for (long i = 1; i <= 40; i++) {
            UserWordBind b = new UserWordBind();
            b.setUserId(1L);
            b.setWordId(i);
            b.setStability(0.9);
            b.setDifficulty(5.0);
            b.setConsecutiveErrors(0);
            b.setNextReviewTime(LocalDateTime.now().plusDays(2));
            binds.add(b);
        }

        when(userWordBindRepository.findByUserId(1L)).thenReturn(binds);
        when(conversationQuizLogRepository.findWordIdsUsedCorrectlySince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(quizRecordRepository.avgGradeByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(1.8);

        AgentService.RecommendResponse resp = agentService.getRecommendation(1L);

        assertEquals(5, resp.getDailyNewWords(), "Low accuracy should reduce new words to the minimum");
        assertEquals("quick_memory", resp.getSuggestedMode());
        assertTrue(resp.getReason().contains("正确率偏低"));
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

    // --- generateSessionReport ---

    // --- generatePassage ---

    @Test
    void testGeneratePassage_fallbackReturnsStructuredStory() throws Exception {
        AgentService.PassageRequest req = new AgentService.PassageRequest();
        req.setWords(List.of("abandon", "benevolent"));
        when(deepSeekClient.chat(anyList())).thenReturn(null);

        AgentService.PassageResponse resp = agentService.generatePassage(req);

        assertNotNull(resp.getTitle());
        assertNotNull(resp.getPassage());
        assertNotNull(resp.getTranslation());
        assertTrue(resp.getPassage().contains("abandon"));
        assertTrue(resp.getPassage().contains("benevolent"));
        assertEquals(2, resp.getWordSentences().size());
        assertTrue(resp.getWordSentences().containsKey("abandon"));
        assertTrue(resp.getWordSentences().containsKey("benevolent"));
    }

    @Test
    void testAnalyzeWord_usesLocalWordDataOnFallback() {
        com.timo.words.modules.word.entity.Word word = new com.timo.words.modules.word.entity.Word();
        word.setId(11L);
        word.setWord("abandon");
        word.setPhonetic("/əˈbændən/");
        word.setPos("v.");

        Meaning meaning = new Meaning();
        meaning.setId(1L);
        meaning.setMeaning("放弃");
        meaning.setPartOfSpeech("v.");

        Example example = new Example();
        example.setId(1L);
        example.setSentence("Never abandon your goals.");
        example.setTranslation("永远不要放弃你的目标。");

        when(wordRepository.findFirstByWordIgnoreCase("abandon")).thenReturn(Optional.of(word));
        when(meaningRepository.findByWordIdOrderBySortOrder(11L)).thenReturn(List.of(meaning));
        when(exampleRepository.findByWordIdOrderByIdAsc(11L)).thenReturn(List.of(example));
        when(deepSeekClient.chat(anyList())).thenThrow(new RuntimeException("offline"));

        AgentService.AnalyzeWordResponse resp = agentService.analyzeWord("abandon");

        assertEquals("/əˈbændən/", resp.getPhonetic());
        assertEquals("放弃", resp.getMeaning());
        assertTrue(resp.getMnemonic().contains("abandon"));
        assertTrue(resp.getUsage().contains("v."));
        assertTrue(resp.getExample().contains("Never abandon your goals."));
    }

    @Test
    void testWeeklyReport_includesInsightsAndComparison() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime weekAgo = now.minusDays(6).toLocalDate().atStartOfDay();
        java.time.LocalDateTime prevWeekAgo = now.minusDays(13).toLocalDate().atStartOfDay();
        java.time.LocalDateTime prevWeekEnd = now.minusDays(7).toLocalDate().atTime(23, 59, 59);

        com.timo.words.modules.study.entity.QuizRecord current1 = new com.timo.words.modules.study.entity.QuizRecord();
        current1.setGrade(4.0);
        com.timo.words.modules.study.entity.QuizRecord current2 = new com.timo.words.modules.study.entity.QuizRecord();
        current2.setGrade(2.0);

        com.timo.words.modules.study.entity.QuizRecord prev1 = new com.timo.words.modules.study.entity.QuizRecord();
        prev1.setGrade(2.0);
        com.timo.words.modules.study.entity.QuizRecord prev2 = new com.timo.words.modules.study.entity.QuizRecord();
        prev2.setGrade(3.0);

        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(current1, current2), List.of(prev1, prev2));
        when(quizRecordRepository.countDistinctWordIdByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(2L);
        when(quizRecordRepository.countStudyDaysSince(eq(1L), any(LocalDateTime.class))).thenReturn(2L);
        when(userWordBindRepository.countMasteredByUserId(1L)).thenReturn(5L);
        when(checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(1L)).thenReturn(Collections.emptyList());

        AgentService.WeeklyReportResponse resp = agentService.getWeeklyReport(1L);

        assertEquals(2, resp.getTotalWords());
        assertEquals(5, resp.getMasteredWords());
        assertEquals(50.0, resp.getAvgAccuracy());
        assertNotNull(resp.getInsights());
        assertFalse(resp.getInsights().isEmpty());
        assertEquals(0.0, resp.getAccuracyDelta());
        assertNotNull(resp.getWeakness());
        assertNotNull(resp.getSuggestion());
        assertTrue(resp.getInsights().stream().anyMatch(s -> s.contains("上周")));
    }

    @Test
    void testSendChat_withoutNickname_promptsNicknameAndReturnsIdle() {
        com.timo.words.modules.user.entity.User user = new com.timo.words.modules.user.entity.User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setNickname(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatSessionRepository.save(any())).thenAnswer(inv -> {
            com.timo.words.modules.agent.entity.ChatSession s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });
        when(deepSeekClient.chatFreeForm(anyList())).thenReturn("你好呀，先从快速记忆开始吧！ {\"state\":\"idle\",\"actions\":[\"开始快速记忆\"]}");

        AgentService.ChatRequest req = new AgentService.ChatRequest();
        req.setUserId(1L);
        req.setMessage("你好");

        AgentService.ChatResponse resp = agentService.sendChat(req);

        assertEquals("idle", resp.getTiMoState());
        assertTrue(resp.getReply().contains("你希望我怎么称呼你"));
        assertTrue(resp.getSuggestedActions().contains("设置昵称"));
    }

    @Nested
    class GenerateSessionReportTests {

        private AgentService.SessionReportRequest makeRequest(int total, int correct, int wrong, String mode) {
            AgentService.SessionReportRequest req = new AgentService.SessionReportRequest();
            req.setStudyMode(mode);
            req.setTotalWords(total);
            req.setCorrectCount(correct);
            req.setWrongCount(wrong);
            req.setElapsedMs(60000L);
            req.setWordTexts(List.of("abandon", "benevolent", "catalyst"));
            req.setWrongWordTexts(wrong > 0 ? List.of("abandon", "benevolent") : List.of());
            return req;
        }

        @Test
        void testDeepSeekSuccess_returnsAiSummary() {
            AgentService.SessionReportRequest req = makeRequest(10, 8, 2, "quick_memory");
            when(deepSeekClient.chatFreeForm(anyList())).thenReturn("正确率80%！今天状态不错，继续加油！");

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            assertEquals("正确率80%！今天状态不错，继续加油！", resp.getSummary());
            assertEquals("success", resp.getTiMoState());
            assertTrue(resp.getActions().contains("开始今日学习"),
                    "Should suggest learning when wrongCount > 0");
        }

        @Test
        void testDeepSeekUnavailable_usesFallback() {
            AgentService.SessionReportRequest req = makeRequest(10, 8, 2, "context_deep");
            when(deepSeekClient.chatFreeForm(anyList())).thenThrow(new RuntimeException("API timeout"));

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            assertTrue(resp.getSummary().contains("太棒了"),
                    "Fallback should praise when accuracy >= 80%");
            assertEquals("success", resp.getTiMoState());
        }

        @Test
        void testDeepSeekReturnsBlank_usesFallback() {
            AgentService.SessionReportRequest req = makeRequest(5, 3, 2, "unified_review");
            when(deepSeekClient.chatFreeForm(anyList())).thenReturn("");

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            assertTrue(resp.getSummary().contains("还不错"),
                    "Fallback should give moderate praise when accuracy 60%");
            assertEquals("idle", resp.getTiMoState());
        }

        @Test
        void testLowAccuracy_fallbackEncouragesReview() {
            AgentService.SessionReportRequest req = makeRequest(10, 3, 7, "quick_memory");
            when(deepSeekClient.chatFreeForm(anyList())).thenThrow(new RuntimeException("API down"));

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            assertTrue(resp.getSummary().contains("巩固"),
                    "Fallback should encourage review when accuracy < 60%");
            assertEquals("idle", resp.getTiMoState());
            assertTrue(resp.getActions().contains("开始今日学习"),
                    "Should include action when wrongCount > 0");
        }

        @Test
        void testModeLabels_mapCorrectly() {
            AgentService.SessionReportRequest req = makeRequest(10, 10, 0, "quick_memory");
            when(deepSeekClient.chatFreeForm(anyList())).thenReturn("太好了！全部正确！");

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.captor();
            verify(deepSeekClient).chatFreeForm(captor.capture());
            String prompt = captor.getValue().get(0).get("content");

            assertTrue(prompt.contains("快速记忆"), "Prompt should contain mode label for quick_memory");
            assertEquals("success", resp.getTiMoState(), "100% accuracy should trigger success state");
        }

        @Test
        void testPerfectAccuracy_noActions() {
            AgentService.SessionReportRequest req = makeRequest(5, 5, 0, "unified_review");
            when(deepSeekClient.chatFreeForm(anyList())).thenThrow(new RuntimeException("API down"));

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            assertTrue(resp.getActions().isEmpty(),
                    "No action suggestions when wrongCount is 0");
        }

        @Test
        void testDefaultModeLabelHandlesUnknownMode() {
            AgentService.SessionReportRequest req = makeRequest(3, 2, 1, "some_custom_mode");
            when(deepSeekClient.chatFreeForm(anyList())).thenThrow(new RuntimeException("API down"));

            AgentService.SessionReportResponse resp = agentService.generateSessionReport(req);

            assertNotNull(resp.getSummary());
            assertFalse(resp.getSummary().isBlank());
            assertEquals("idle", resp.getTiMoState());
        }
    }

    // --- generateQuestions ---

    @Test
    void testGenerateQuestions_deepSeekSuccess_parsesResponse() throws Exception {
        String json = """
                {"questions":[{"word":"abandon","question":"abandon 的中文释义是什么？","options":["放弃","到达","加速","接受"],"correctIndex":0,"explanation":"abandon 意为放弃"}]}
                """;
        when(deepSeekClient.chat(anyList())).thenReturn(json);
        com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode rootNode = realMapper.readTree(json);
        when(objectMapper.readTree(json)).thenReturn(rootNode);
        when(objectMapper.convertValue(any(), eq(List.class))).thenReturn(List.of("放弃", "到达", "加速", "接受"));

        Map<String, Object> result = agentService.generateQuestions(List.of("abandon"));

        assertNotNull(result.get("questions"));
        List<?> questions = (List<?>) result.get("questions");
        assertEquals(1, questions.size());
    }

    @Test
    void testGenerateQuestions_fallbackUsesRealMeanings() {
        when(deepSeekClient.chat(anyList())).thenThrow(new RuntimeException("API down"));

        com.timo.words.modules.word.entity.Word word = new com.timo.words.modules.word.entity.Word();
        word.setId(1L);
        word.setWord("abandon");
        when(wordRepository.findByWordIn(List.of("abandon"))).thenReturn(List.of(word));

        Meaning meaning = new Meaning();
        meaning.setId(1L);
        meaning.setMeaning("放弃");
        meaning.setPartOfSpeech("v.");
        when(meaningRepository.findByWordIdOrderBySortOrder(1L)).thenReturn(List.of(meaning));

        Map<String, Object> result = agentService.generateQuestions(List.of("abandon"));

        List<?> questions = (List<?>) result.get("questions");
        assertEquals(1, questions.size());
        Map<?, ?> q = (Map<?, ?>) questions.get(0);
        assertEquals("abandon", q.get("word"));
        List<?> options = (List<?>) q.get("options");
        assertEquals("放弃", options.get(0));
        assertFalse(options.contains("释义A"), "Fallback should not contain placeholder options");
    }

    // --- getStubbornWordsAnalysis ---

    @Test
    void testGetStubbornWordsAnalysis_returnsStubbornWords() {
        UserWordBind bind = new UserWordBind();
        bind.setWordId(10L);
        bind.setDifficulty(8.0);
        bind.setStability(0.5);
        bind.setConsecutiveErrors(4);
        bind.setStubbornSince(LocalDateTime.now().minusDays(3));
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L)).thenReturn(List.of(bind));

        Map<String, Object> result = agentService.getStubbornWordsAnalysis(1L);

        assertEquals(1, result.get("count"));
        List<?> words = (List<?>) result.get("words");
        assertEquals(1, words.size());
        Map<?, ?> w = (Map<?, ?>) words.get(0);
        assertEquals(10L, w.get("wordId"));
        assertEquals(8.0, w.get("difficulty"));
        assertEquals(4, w.get("consecutiveErrors"));
    }

    @Test
    void testGetStubbornWordsAnalysis_noStubbornWords() {
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L)).thenReturn(Collections.emptyList());

        Map<String, Object> result = agentService.getStubbornWordsAnalysis(1L);

        assertEquals(0, result.get("count"));
        assertTrue(((List<?>) result.get("words")).isEmpty());
    }

    // --- loadHistory ---

    @Test
    void testLoadHistory_validSession_returnsMessages() {
        com.timo.words.modules.agent.entity.ChatSession session = new com.timo.words.modules.agent.entity.ChatSession();
        session.setId(50L);
        session.setUserId(1L);
        when(chatSessionRepository.findById(50L)).thenReturn(Optional.of(session));

        ChatMessage msg = new ChatMessage();
        msg.setRole("user");
        msg.setContent("你好");
        msg.setCreatedAt(LocalDateTime.now());
        when(chatMessageRepository.findTop50BySessionIdOrderByCreatedAtAsc(50L)).thenReturn(List.of(msg));

        List<Map<String, String>> result = agentService.loadHistory(1L, 50L);

        assertEquals(1, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("你好", result.get(0).get("content"));
    }

    @Test
    void testLoadHistory_invalidSession_returnsEmpty() {
        when(chatSessionRepository.findById(99L)).thenReturn(Optional.empty());

        List<Map<String, String>> result = agentService.loadHistory(1L, 99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void testLoadHistory_wrongUser_returnsEmpty() {
        com.timo.words.modules.agent.entity.ChatSession session = new com.timo.words.modules.agent.entity.ChatSession();
        session.setId(50L);
        session.setUserId(999L);
        when(chatSessionRepository.findById(50L)).thenReturn(Optional.of(session));

        List<Map<String, String>> result = agentService.loadHistory(1L, 50L);

        assertTrue(result.isEmpty(), "Should return empty when session belongs to another user");
    }

    // --- sendChat ---

    @Test
    void testSendChat_deepSeekSuccess_returnsReply() {
        com.timo.words.modules.user.entity.User user = new com.timo.words.modules.user.entity.User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setNickname("XiaoMing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatSessionRepository.save(any())).thenAnswer(inv -> {
            com.timo.words.modules.agent.entity.ChatSession s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());
        when(userWordBindRepository.countByUserId(1L)).thenReturn(0L);
        when(userWordBindRepository.countMasteredByUserId(1L)).thenReturn(0L);
        when(userWordBindRepository.countDueByUserId(eq(1L), any())).thenReturn(0L);
        when(userWordBindRepository.countStubbornByUserId(1L)).thenReturn(0L);
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(1L)).thenReturn(Collections.emptyList());
        when(deepSeekClient.chatFreeForm(anyList())).thenReturn("Hello XiaoMing! Ready to study today?");

        AgentService.ChatRequest req = new AgentService.ChatRequest();
        req.setUserId(1L);
        req.setMessage("hello");

        AgentService.ChatResponse resp = agentService.sendChat(req);

        assertEquals(10L, resp.getSessionId());
        assertEquals("idle", resp.getTiMoState());
        assertNotNull(resp.getReply());
        assertTrue(resp.getReply().contains("XiaoMing"));
    }

    @Test
    void testSendChat_deepSeekFailure_usesFallback() {
        com.timo.words.modules.user.entity.User user = new com.timo.words.modules.user.entity.User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setNickname("小明");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(chatSessionRepository.save(any())).thenAnswer(inv -> {
            com.timo.words.modules.agent.entity.ChatSession s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());
        when(deepSeekClient.chatFreeForm(anyList())).thenThrow(new RuntimeException("API down"));

        AgentService.ChatRequest req = new AgentService.ChatRequest();
        req.setUserId(1L);
        req.setMessage("我想背单词");

        AgentService.ChatResponse resp = agentService.sendChat(req);

        assertEquals("idle", resp.getTiMoState());
        assertNotNull(resp.getReply());
        assertTrue(resp.getReply().contains("小明"));
    }

    // --- generatePassage DeepSeek success ---

    @Test
    void testGeneratePassage_deepSeekSuccess_returnsAiPassage() throws Exception {
        String json = """
                {"title":"A Test Story","passage":"This is a test story about abandon and benevolent. The story continues with enough words to meet the minimum requirement. We need at least one hundred and twenty words for the passage to pass quality validation. Let me keep writing to reach that threshold. The character decided to abandon his old ways and become a more benevolent person. He started volunteering at the local shelter, helping those in need. Every day he would wake up early and prepare meals for the homeless. His transformation inspired others around him to follow suit. The community began to change as more people joined his cause. What started as one man's journey became a movement of kindness.","translation":"这是一个测试故事。","wordSentences":{"abandon":"He decided to abandon the plan.","benevolent":"She was a benevolent leader."}}
                """;
        when(deepSeekClient.chat(anyList())).thenReturn(json);

        com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode rootNode = realMapper.readTree(json);
        when(objectMapper.readTree(json)).thenReturn(rootNode);

        AgentService.PassageRequest req = new AgentService.PassageRequest();
        req.setWords(List.of("abandon", "benevolent"));

        AgentService.PassageResponse resp = agentService.generatePassage(req);

        assertEquals("A Test Story", resp.getTitle());
        assertNotNull(resp.getPassage());
        assertTrue(resp.getPassage().contains("abandon"));
        assertTrue(resp.getPassage().contains("benevolent"));
        assertNotNull(resp.getTranslation());
    }

    // --- analyzeWord DeepSeek success ---

    @Test
    void testAnalyzeWord_deepSeekSuccess_parsesJson() throws Exception {
        com.timo.words.modules.word.entity.Word word = new com.timo.words.modules.word.entity.Word();
        word.setId(11L);
        word.setWord("abandon");
        word.setPhonetic("/əˈbændən/");
        when(wordRepository.findFirstByWordIgnoreCase("abandon")).thenReturn(Optional.of(word));
        when(meaningRepository.findByWordIdOrderBySortOrder(11L)).thenReturn(Collections.emptyList());
        when(exampleRepository.findByWordIdOrderByIdAsc(11L)).thenReturn(Collections.emptyList());

        String json = """
                {"phonetic":"/əˈbændən/","meaning":"放弃","etymology":"a- (away) + bandon (control)","mnemonic":"a-ban-don → 一个禁令 → 放弃","usage":"abandon hope, abandon ship","synonyms":"desert, forsake, give up","example":"He decided to abandon the project. — 他决定放弃这个项目。"}
                """;
        when(deepSeekClient.chat(anyList())).thenReturn(json);

        com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode rootNode = realMapper.readTree(json);
        when(objectMapper.readTree(json)).thenReturn(rootNode);

        AgentService.AnalyzeWordResponse resp = agentService.analyzeWord("abandon");

        assertEquals("/əˈbændən/", resp.getPhonetic());
        assertEquals("放弃", resp.getMeaning());
        assertTrue(resp.getEtymology().contains("bandon"));
        assertTrue(resp.getMnemonic().contains("禁令"));
        assertTrue(resp.getUsage().contains("abandon hope"));
        assertTrue(resp.getSynonyms().contains("desert"));
        assertTrue(resp.getExample().contains("abandon the project"));
    }

    // --- planSmartSession (Wave 5) ---

    @Test
    void testPlanSmartSession_emptyQueue_returnsQuickMemoryWarmup() {
        // Arrange: no due, no stubborn, no reverse candidates, no exam plan, no fatigue
        when(userWordBindRepository.countDueByUserId(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        when(userWordBindRepository.countStubbornByUserId(1L)).thenReturn(0L);
        when(userWordBindRepository.findReverseRecallCandidates(eq(1L), any()))
                .thenReturn(Collections.emptyList());
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(quizRecordRepository.countByUserIdSinceAndStudyMode(eq(1L), any(LocalDateTime.class), anyString()))
                .thenReturn(0L);
        when(quizRecordRepository.countByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        // Act
        AgentService.SmartSessionPlanDTO plan = agentService.planSmartSession(1L, 10);

        // Assert
        assertNotNull(plan);
        assertEquals(10, plan.getAvailableMinutes());
        assertFalse(plan.isFatigueWarning(), "No recent activity = no fatigue");
        assertNotNull(plan.getSteps());
        // Without due/stubborn/reverse but with default new-word quota 15, the planner schedules new words.
        assertFalse(plan.getSteps().isEmpty(), "Should always propose at least one step");
        assertEquals("quick_memory", plan.getSteps().get(0).getMode(),
                "Empty queue with new-word quota should default to quick_memory");
        assertNotNull(plan.getSummary());
        assertTrue(plan.getSummary().contains("10 分钟方案"));
    }

    @Test
    void testPlanSmartSession_withDueWords_prioritizesReview() {
        // Arrange: 8 due words, no stubborn, no reverse, default exam plan, no fatigue
        when(userWordBindRepository.countDueByUserId(eq(1L), any(LocalDateTime.class))).thenReturn(8L);
        when(userWordBindRepository.countStubbornByUserId(1L)).thenReturn(0L);
        when(userWordBindRepository.findReverseRecallCandidates(eq(1L), any()))
                .thenReturn(Collections.emptyList());
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(quizRecordRepository.countByUserIdSinceAndStudyMode(eq(1L), any(LocalDateTime.class), anyString()))
                .thenReturn(0L);
        when(quizRecordRepository.countByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        // Act
        AgentService.SmartSessionPlanDTO plan = agentService.planSmartSession(1L, 10);

        // Assert
        assertNotNull(plan);
        assertFalse(plan.getSteps().isEmpty());
        AgentService.SessionStep first = plan.getSteps().get(0);
        assertEquals("unified_review", first.getMode(),
                "Due words must take priority over new-word quota");
        assertTrue(first.getWordCount() > 0 && first.getWordCount() <= 8);
        assertTrue(first.getReason().contains("到期"));
        assertNotNull(first.getNavigationUrl());
        assertTrue(first.getNavigationUrl().startsWith("/review?source=smart-session"),
                "Navigation URL should target /review with smart-session source");
    }

    @Test
    void testPlanSmartSession_fatigued_avoidsContextDeep() {
        // Arrange: 5 stubborn words but user is fatigued (>=30 quizzes in last 30 min)
        when(userWordBindRepository.countDueByUserId(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        when(userWordBindRepository.countStubbornByUserId(1L)).thenReturn(5L);
        when(userWordBindRepository.findReverseRecallCandidates(eq(1L), any()))
                .thenReturn(Collections.emptyList());
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(quizRecordRepository.countByUserIdSinceAndStudyMode(eq(1L), any(LocalDateTime.class), anyString()))
                .thenReturn(0L);
        when(quizRecordRepository.countByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(35L);

        // Act: ask for 15 minutes — would normally fit a context_deep group (8 min)
        AgentService.SmartSessionPlanDTO plan = agentService.planSmartSession(1L, 15);

        // Assert
        assertTrue(plan.isFatigueWarning(), "Fatigue should be detected");
        assertTrue(plan.getSteps().stream().noneMatch(s -> "context_deep".equals(s.getMode())),
                "Fatigued user should not be routed to context_deep");
        // Fatigue cuts the practical budget; total estimated should be within ~5 min
        assertTrue(plan.getTotalEstimatedMinutes() <= 6,
                "Fatigued plan should be a short burst");
    }

    @Test
    void testPlanSmartSession_clampsAvailableMinutes() {
        // Arrange: minimal mocks
        when(userWordBindRepository.countDueByUserId(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        when(userWordBindRepository.countStubbornByUserId(1L)).thenReturn(0L);
        when(userWordBindRepository.findReverseRecallCandidates(eq(1L), any()))
                .thenReturn(Collections.emptyList());
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(quizRecordRepository.countByUserIdSinceAndStudyMode(eq(1L), any(LocalDateTime.class), anyString()))
                .thenReturn(0L);
        when(quizRecordRepository.countByUserIdSince(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        // Pass a huge value — service clamps to 60
        AgentService.SmartSessionPlanDTO plan = agentService.planSmartSession(1L, 9999);

        assertEquals(60, plan.getAvailableMinutes(), "Service should clamp input to 60 minutes");
    }

    // --- Realtime Nudge (Wave 6 — Feature A) ---

    @Test
    void testEvaluateRealtimeNudge_noRecords_returnsEmpty() {
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        Optional<AgentService.RealtimeNudgeDTO> result =
                agentService.evaluateRealtimeNudge(1L, "quick_memory");

        assertTrue(result.isEmpty(), "No recent records should return empty");
    }

    @Test
    void testEvaluateRealtimeNudge_threeTionFailures_returnsTionPattern() {
        // Arrange: three failures on -tion words within last 5 min
        com.timo.words.modules.study.entity.QuizRecord r1 = new com.timo.words.modules.study.entity.QuizRecord();
        r1.setWordId(101L);
        r1.setGrade(1.0);
        r1.setStudyMode("quick_memory");
        r1.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        com.timo.words.modules.study.entity.QuizRecord r2 = new com.timo.words.modules.study.entity.QuizRecord();
        r2.setWordId(102L);
        r2.setGrade(2.0);
        r2.setStudyMode("quick_memory");
        r2.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        com.timo.words.modules.study.entity.QuizRecord r3 = new com.timo.words.modules.study.entity.QuizRecord();
        r3.setWordId(103L);
        r3.setGrade(1.0);
        r3.setStudyMode("quick_memory");
        r3.setCreatedAt(LocalDateTime.now().minusMinutes(3));

        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(r1, r2, r3));

        com.timo.words.modules.word.entity.Word w1 = new com.timo.words.modules.word.entity.Word();
        w1.setId(101L); w1.setWord("attention");
        com.timo.words.modules.word.entity.Word w2 = new com.timo.words.modules.word.entity.Word();
        w2.setId(102L); w2.setWord("decision");  // -sion not -tion → ensure only tion suffix matches
        com.timo.words.modules.word.entity.Word w3 = new com.timo.words.modules.word.entity.Word();
        w3.setId(103L); w3.setWord("emotion");

        // Update w2 to a -tion word to truly all share -tion
        w2.setWord("creation");
        when(wordRepository.findByIdIn(anyList())).thenReturn(List.of(w1, w2, w3));

        Optional<AgentService.RealtimeNudgeDTO> result =
                agentService.evaluateRealtimeNudge(1L, "quick_memory");

        assertTrue(result.isPresent(), "Three -tion failures should trigger nudge");
        AgentService.RealtimeNudgeDTO dto = result.get();
        assertEquals("-tion 后缀错误", dto.getPattern());
        assertTrue(dto.getMessage().contains("-tion"));
        assertEquals("open_chat_topic", dto.getSuggestedAction());
        assertNotNull(dto.getSuggestedRoute());
    }

    @Test
    void testEvaluateRealtimeNudge_threeLongWordsNoSuffix_returnsLongPattern() {
        com.timo.words.modules.study.entity.QuizRecord r1 = new com.timo.words.modules.study.entity.QuizRecord();
        r1.setWordId(201L);
        r1.setGrade(1.0);
        r1.setStudyMode("quick_memory");
        r1.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        com.timo.words.modules.study.entity.QuizRecord r2 = new com.timo.words.modules.study.entity.QuizRecord();
        r2.setWordId(202L);
        r2.setGrade(2.0);
        r2.setStudyMode("quick_memory");
        r2.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        com.timo.words.modules.study.entity.QuizRecord r3 = new com.timo.words.modules.study.entity.QuizRecord();
        r3.setWordId(203L);
        r3.setGrade(1.0);
        r3.setStudyMode("quick_memory");
        r3.setCreatedAt(LocalDateTime.now().minusMinutes(3));

        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(r1, r2, r3));

        com.timo.words.modules.word.entity.Word w1 = new com.timo.words.modules.word.entity.Word();
        w1.setId(201L); w1.setWord("benevolent");   // 10
        com.timo.words.modules.word.entity.Word w2 = new com.timo.words.modules.word.entity.Word();
        w2.setId(202L); w2.setWord("ambiguous");    // 9
        com.timo.words.modules.word.entity.Word w3 = new com.timo.words.modules.word.entity.Word();
        w3.setId(203L); w3.setWord("perpetual");    // 9
        when(wordRepository.findByIdIn(anyList())).thenReturn(List.of(w1, w2, w3));

        Optional<AgentService.RealtimeNudgeDTO> result =
                agentService.evaluateRealtimeNudge(1L, "quick_memory");

        assertTrue(result.isPresent(), "Three long-word failures (no common suffix) should trigger nudge");
        AgentService.RealtimeNudgeDTO dto = result.get();
        assertEquals("长单词疲劳", dto.getPattern());
        assertEquals("switch_to_quick_memory", dto.getSuggestedAction());
        assertEquals("/quick-memory", dto.getSuggestedRoute());
    }

    @Test
    void testEvaluateRealtimeNudge_twoFailuresOnly_returnsEmpty() {
        com.timo.words.modules.study.entity.QuizRecord r1 = new com.timo.words.modules.study.entity.QuizRecord();
        r1.setWordId(301L);
        r1.setGrade(1.0);
        r1.setStudyMode("quick_memory");
        r1.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        com.timo.words.modules.study.entity.QuizRecord r2 = new com.timo.words.modules.study.entity.QuizRecord();
        r2.setWordId(302L);
        r2.setGrade(2.0);
        r2.setStudyMode("quick_memory");
        r2.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(r1, r2));

        Optional<AgentService.RealtimeNudgeDTO> result =
                agentService.evaluateRealtimeNudge(1L, "quick_memory");

        assertTrue(result.isEmpty(), "Fewer than 3 failures should not trigger nudge");
    }

    // --- Time-of-Day Analysis (Wave 6 — Feature B) ---

    @Test
    void testBuildTimeOfDayAnalysis_emptyRecords_returnsNull() {
        AgentService.TimeOfDayAnalysis dto =
                agentService.buildTimeOfDayAnalysis(Collections.emptyList());
        assertNull(dto, "Empty input should return null");
    }

    @Test
    void testBuildTimeOfDayAnalysis_insufficientBucket_returnsNull() {
        // Only 3 records in same hour — below the 5 threshold
        List<com.timo.words.modules.study.entity.QuizRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            com.timo.words.modules.study.entity.QuizRecord r = new com.timo.words.modules.study.entity.QuizRecord();
            r.setGrade(4.0);
            r.setCreatedAt(LocalDateTime.now().withHour(9).withMinute(i * 5));
            records.add(r);
        }
        AgentService.TimeOfDayAnalysis dto = agentService.buildTimeOfDayAnalysis(records);
        assertNull(dto, "Sparse data should return null");
    }

    @Test
    void testBuildTimeOfDayAnalysis_identifiesBestHour() {
        // 6 records at 9:00 — 5 correct (83.3%), 6 records at 21:00 — 1 correct (16.7%)
        List<com.timo.words.modules.study.entity.QuizRecord> records = new java.util.ArrayList<>();
        java.time.LocalDate base = java.time.LocalDate.now();
        for (int i = 0; i < 6; i++) {
            com.timo.words.modules.study.entity.QuizRecord r = new com.timo.words.modules.study.entity.QuizRecord();
            r.setGrade(i < 5 ? 4.0 : 1.0);
            r.setCreatedAt(base.atTime(9, i * 3));
            records.add(r);
        }
        for (int i = 0; i < 6; i++) {
            com.timo.words.modules.study.entity.QuizRecord r = new com.timo.words.modules.study.entity.QuizRecord();
            r.setGrade(i == 0 ? 4.0 : 1.0);
            r.setCreatedAt(base.atTime(21, i * 3));
            records.add(r);
        }

        AgentService.TimeOfDayAnalysis dto = agentService.buildTimeOfDayAnalysis(records);

        assertNotNull(dto, "Should return analysis when buckets are populated");
        assertEquals("9:00-10:00", dto.getBestHourRange());
        assertTrue(dto.getBestHourAccuracy() > 0.8, "Best hour accuracy should be ~83%");
        assertEquals("21:00-22:00", dto.getWorstHourRange());
        assertTrue(dto.getWorstHourAccuracy() < 0.3);
        assertNotNull(dto.getRecommendation());
        assertTrue(dto.getRecommendation().contains("9:00-10:00"));
    }
}
