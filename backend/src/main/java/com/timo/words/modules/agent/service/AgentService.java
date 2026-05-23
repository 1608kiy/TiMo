package com.timo.words.modules.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timo.words.infrastructure.ai.DeepSeekClient;
import com.timo.words.modules.agent.entity.ChatMessage;
import com.timo.words.modules.agent.entity.ChatSession;
import com.timo.words.modules.agent.entity.ConversationQuizLog;
import com.timo.words.modules.agent.repository.ChatMessageRepository;
import com.timo.words.modules.agent.repository.ChatSessionRepository;
import com.timo.words.modules.agent.repository.ConversationQuizLogRepository;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.examplan.entity.ExamPlan;
import com.timo.words.modules.examplan.repository.ExamPlanRepository;
import com.timo.words.modules.calendar.repository.CheckinRecordRepository;
import com.timo.words.modules.calendar.entity.CheckinRecord;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.entity.Example;
import com.timo.words.modules.word.entity.Meaning;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.ExampleRepository;
import com.timo.words.modules.word.repository.MeaningRepository;
import com.timo.words.modules.word.repository.WordRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final DeepSeekClient deepSeekClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WordRepository wordRepository;
    private final UserWordBindRepository userWordBindRepository;
    private final QuizRecordRepository quizRecordRepository;
    private final CheckinRecordRepository checkinRecordRepository;
    private final ConversationQuizLogRepository conversationQuizLogRepository;
    private final ExamPlanRepository examPlanRepository;
    private final ObjectMapper objectMapper;
    private final MeaningRepository meaningRepository;
    private final ExampleRepository exampleRepository;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是 TiMo，一个温柔、专业、可信赖的备考教练和单词学习助手。
            你的风格必须根据场景切换，但始终保持善意、支持感和尊重：
            - 日常对话：温柔鼓励，像一个会认真倾听的朋友
            - 用户明显懈怠、拖延或连续未打卡：可以适当严肃督促，但绝不冒犯、讽刺或阴阳怪气
            - 数据分析、进度总结、周报：使用专业导师口吻，客观、具体、基于数据

            你的职责：
            1. 回答用户关于单词学习的问题
            2. 根据用户的实时学习数据和薄弱词（顽固词）给出精准建议
            3. 鼓励用户坚持学习，适时提醒
            4. 解释单词的用法、搭配、记忆技巧
            5. 如果用户尚未设置昵称，先自然询问："你希望我怎么称呼你？"

            回复要求：
            - 简洁友好，像朋友聊天一样，避免长篇大论
            - 如果用户问的是具体单词，给出释义、例句和记忆技巧
            - 如果系统传给你了用户近期的【高频错词/顽固词】，可以主动提出来，但不要反复推销
            - 如果用户情绪低落，优先共情，再给一条轻量建议
            - 如果用户明显状态不错，少说教，多肯定

            回复格式要求：
            - 在回复末尾用JSON格式附带元数据（单独一行，不放在代码块中）：
            {"state":"<idle|success|alert>","actions":["<可选操作标签>"]}
            - state字段：idle=正常回复，success=用户有进步或完成任务，alert=需要关注学习进度
            - actions字段：可选的操作建议标签，如"开始快速记忆"、"开始深度学习"、"查看周报"、"开始今日学习"
            - 如果你主动建议用户攻克顽固词，可以加上 "开始深度学习"
            - 如果不需要actions，返回空数组
            """;

    // --- DTOs ---

    @Data
    public static class ChatRequest {
        private Long userId;
        @NotBlank(message = "消息不能为空")
        private String message;
        private Long sessionId;
    }

    @Data
    public static class ChatResponse {
        private Long sessionId;
        private String reply;
        private String tiMoState; // idle, thinking, success, alert
        private List<String> suggestedActions;
    }

    @Data
    public static class RecommendResponse {
        private int dailyNewWords;
        private int dailyReviewWords;
        private String suggestedMode;
        private String reason;
        private List<WeakWordDTO> weakWords;
    }

    @Data
    public static class WeakWordDTO {
        private Long wordId;
        private double difficulty;
        private int consecutiveErrors;
    }

    @Data
    public static class SessionReportRequest {
        @NotBlank(message = "学习模式不能为空")
        private String studyMode;
        private int totalWords;
        private int correctCount;
        private int wrongCount;
        private long elapsedMs;
        private List<String> wordTexts;
        private List<String> wrongWordTexts;
    }

    @Data
    public static class SessionReportResponse {
        private String summary;
        private String tiMoState;
        private List<String> actions;
    }

    @Data
    public static class WeeklyReportResponse {
        private int totalStudied;
        private int totalWords;
        private int newWordsLearned;
        private int reviewsCompleted;
        private int masteredWords;
        private double avgAccuracy;
        private double accuracyDelta;
        private int studyDays;
        private int longestStreak;
        private String summary;
        private String weakness;
        private String suggestion;
        private List<String> insights;
        private List<String> suggestions;
        /** Wave 6 — 时段偏好分析，数据不足时为 null。 */
        private TimeOfDayAnalysis timeAnalysis;
    }

    @Data
    public static class PassageRequest {
        @jakarta.validation.constraints.NotEmpty(message = "单词列表不能为空")
        private List<String> words;
    }

    @Data
    public static class PassageResponse {
        private String title;
        private String passage;
        private String translation;
        private Map<String, String> wordSentences;
    }

    @Data
    public static class LogQuizRequest {
        private Long wordId;
        private Long sessionId;
        private Boolean usedCorrectly;
    }

    @Data
    public static class ProgressAlertResponse {
        private boolean hasAlert;
        private String message;
        private double expectedWords;
        private double actualWords;
        private double progressRatio;
        private String suggestedAction;
    }

    @Data
    public static class AnalyzeWordRequest {
        @NotBlank(message = "单词不能为空")
        private String word;
    }

    @Data
    public static class AnalyzeWordResponse {
        private String phonetic;
        private String meaning;
        private String etymology;
        private String mnemonic;
        private String usage;
        private String synonyms;
        private String example;
    }

    // ---- Smart Session Planning (Wave 5) ----

    @Data
    public static class SmartSessionPlanDTO {
        private int totalEstimatedMinutes;
        private int availableMinutes;
        /** true 时前端可显示"建议短时间冲刺"。 */
        private boolean fatigueWarning;
        private List<SessionStep> steps;
        /** 一句话总结，例如 "10 分钟方案：先复习 8 个到期词，再过 3 个新词"。 */
        private String summary;
    }

    @Data
    public static class SessionStep {
        /** quick_memory / context_deep / unified_review / reverse_recall */
        private String mode;
        private int wordCount;
        private int estimatedMinutes;
        private String reason;
        /** 例如 /study/quick-memory?source=smart-session&words=5 */
        private String navigationUrl;
    }

    @Data
    public static class SmartSessionRequest {
        private int availableMinutes;
    }

    // ---- Realtime Nudge (Wave 6 — Feature A) ----

    @Data
    public static class RealtimeNudgeDTO {
        /** "-tion 后缀错误" / "长单词疲劳" / "其他" */
        private String pattern;
        /** 完整的 TiMo 话术，可直接显示 */
        private String message;
        /** "switch_to_quick_memory" / "open_chat_topic" / "take_break" */
        private String suggestedAction;
        /** 路由路径，例如 "/study/quick-memory" */
        private String suggestedRoute;
    }

    @Data
    public static class NudgeRequest {
        private String studyMode;
    }

    // ---- Time-of-Day Analysis (Wave 6 — Feature B) ----

    @Data
    public static class TimeOfDayAnalysis {
        /** 例如 "9:00-10:00" */
        private String bestHourRange;
        /** 0.0 ~ 1.0 */
        private double bestHourAccuracy;
        private String worstHourRange;
        private double worstHourAccuracy;
        private int avgSessionLengthMinutes;
        private String recommendation;
    }

    // --- Chat ---

    public ChatResponse sendChat(ChatRequest req) {
        Long userId = req.getUserId();
        String message = req.getMessage();
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }

        // Get or create session
        ChatSession session = null;
        if (req.getSessionId() != null) {
            session = chatSessionRepository.findById(req.getSessionId()).orElse(null);
            if (session != null && !session.getUserId().equals(userId)) {
                throw new IllegalArgumentException("无权访问该会话");
            }
        }
        if (session == null) {
            session = new ChatSession();
            session.setUserId(userId);
            session.setConversationType("general");
            session.setTitle(req.getMessage().substring(0, Math.min(20, req.getMessage().length())));
            session = chatSessionRepository.save(session);
        }

        // Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("user");
        userMsg.setContent(req.getMessage());
        chatMessageRepository.save(userMsg);

        // Build dynamic system prompt with learning stats
        User user = userRepository.findById(userId).orElse(null);
        String userInfo = buildUserInfo(user);
        boolean needsNickname = user != null && (user.getNickname() == null || user.getNickname().isBlank());
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted() + "\n\n用户信息：" + userInfo;
        if (needsNickname) {
            systemPrompt += "\n\n额外要求：如果当前是首次或尚未收集昵称的对话，请先自然询问用户希望被怎么称呼，再继续帮助。";
        }

        // Build structured chat history
        List<Map<String, String>> historyMessages = buildStructuredHistory(session.getId());

        // Call DeepSeek
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(historyMessages);
        messages.add(Map.of("role", "user", "content", req.getMessage()));

        String rawReply;
        try {
            rawReply = deepSeekClient.chatFreeForm(messages);
        } catch (Exception e) {
            log.error("DeepSeek call failed for userId={}", userId, e);
            rawReply = null;
        }

        // Fallback if API fails
        if (rawReply == null) {
            log.warn("Using fallback reply for userId={} (DeepSeek unavailable)", userId);
            rawReply = generateFallbackReply(req.getMessage(), user);
        }

        // Parse tiMoState and actions from AI reply
        String tiMoState = "idle";
        List<String> actions = new ArrayList<>();
        String reply = rawReply;

        try {
            // Try to extract JSON metadata from the end of the reply
            String trimmed = rawReply.trim();
            int jsonStart = trimmed.lastIndexOf('{');
            if (jsonStart >= 0) {
                String jsonPart = trimmed.substring(jsonStart);
                JsonNode meta = objectMapper.readTree(jsonPart);
                tiMoState = meta.path("state").asText("idle");
                if (meta.has("actions")) {
                    meta.get("actions").forEach(a -> actions.add(a.asText()));
                }
                // Strip the metadata JSON from the visible reply
                reply = trimmed.substring(0, jsonStart).trim();
                if (reply.isEmpty()) reply = rawReply;
            }
        } catch (Exception e) {
            log.debug("Failed to parse AI metadata, using defaults");
        }

        // Validate tiMoState
        if (!Set.of("idle", "success", "alert", "thinking").contains(tiMoState)) {
            tiMoState = "idle";
        }

        if (needsNickname && tiMoState.equals("alert")) {
            tiMoState = "idle";
        }

        if (needsNickname) {
            if (!reply.contains("怎么称呼") && !reply.contains("称呼你") && !reply.contains("怎么叫你")) {
                reply = "你希望我怎么称呼你？\n\n" + reply;
            }
            tiMoState = "idle";
            if (!actions.contains("设置昵称")) {
                actions.add(0, "设置昵称");
            }
        }

        // Save assistant message with card data
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        if (!actions.isEmpty()) {
            try {
                assistantMsg.setSuggestedActions(objectMapper.writeValueAsString(actions));
            } catch (Exception e) { log.warn("Failed to serialize suggested actions", e); }
        }
        chatMessageRepository.save(assistantMsg);

        ChatResponse resp = new ChatResponse();
        resp.setSessionId(session.getId());
        resp.setReply(reply);
        resp.setTiMoState(tiMoState);
        resp.setSuggestedActions(actions);
        return resp;
    }

    // --- Load History ---

    public List<Map<String, String>> loadHistory(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.getUserId().equals(userId)) {
            return Collections.emptyList();
        }
        List<ChatMessage> msgs = chatMessageRepository.findTop50BySessionIdOrderByCreatedAtAsc(sessionId);
        List<Map<String, String>> result = new ArrayList<>();
        for (ChatMessage m : msgs) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            item.put("timestamp", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
            if (m.getSuggestedActions() != null) {
                item.put("actions", m.getSuggestedActions());
            }
            result.add(item);
        }
        return result;
    }

    // --- Stubborn Words Analysis ---

    public Map<String, Object> getStubbornWordsAnalysis(Long userId) {
        List<UserWordBind> stubbornBinds = userWordBindRepository.findByUserIdAndIsStubbornTrue(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", stubbornBinds.size());

        List<Map<String, Object>> words = new ArrayList<>();
        for (UserWordBind bind : stubbornBinds) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("wordId", bind.getWordId());
            w.put("difficulty", bind.getDifficulty());
            w.put("stability", bind.getStability());
            w.put("consecutiveErrors", bind.getConsecutiveErrors());
            w.put("since", bind.getStubbornSince() != null ? bind.getStubbornSince().toString() : null);
            words.add(w);
        }
        result.put("words", words);
        return result;
    }

    // --- Conversation Quiz Logging ---

    @org.springframework.transaction.annotation.Transactional
    public void logConversationQuiz(Long userId, LogQuizRequest req) {
        ConversationQuizLog logEntry = new ConversationQuizLog();
        logEntry.setUserId(userId);
        logEntry.setWordId(req.getWordId());
        logEntry.setSessionId(req.getSessionId());
        logEntry.setUsedCorrectly(req.getUsedCorrectly());
        conversationQuizLogRepository.save(logEntry);
    }

    // --- Recommend ---

    public RecommendResponse getRecommendation(Long userId) {
        List<UserWordBind> binds = userWordBindRepository.findByUserId(userId);

        int totalWordsStudied = binds.size();

        // Filter out words mastered in conversation within last 3 days
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        Set<Long> conversationMasteredWordIds = new HashSet<>(
                conversationQuizLogRepository.findWordIdsUsedCorrectlySince(userId, threeDaysAgo));

        int masteredCount = (int) binds.stream()
                .filter(b -> b.getStability() != null && b.getStability() >= 1.2)
                .filter(b -> !conversationMasteredWordIds.contains(b.getWordId()))
                .count();

        int dueCount = (int) binds.stream()
                .filter(b -> b.getNextReviewTime() != null && b.getNextReviewTime().isBefore(LocalDateTime.now()))
                .filter(b -> !conversationMasteredWordIds.contains(b.getWordId()))
                .count();

        long errorProneCount = binds.stream()
                .filter(b -> b.getConsecutiveErrors() != null && b.getConsecutiveErrors() >= 2)
                .filter(b -> !conversationMasteredWordIds.contains(b.getWordId()))
                .count();

        Double recentAccuracy = quizRecordRepository.avgGradeByUserIdSince(userId, LocalDateTime.now().minusDays(7));
        double accuracyPercent = recentAccuracy == null ? 0.0 : Math.max(0.0, Math.min(100.0, (recentAccuracy - 1.0) / 3.0 * 100.0));

        boolean coldStart = totalWordsStudied < 30;
        boolean lowAccuracy = accuracyPercent > 0 && accuracyPercent < 60;
        boolean highAccuracy = accuracyPercent >= 85;

        int dailyNew;
        if (coldStart) {
            dailyNew = totalWordsStudied < 10 ? 5 : totalWordsStudied < 20 ? 8 : 12;
        } else if (lowAccuracy) {
            dailyNew = 5;
        } else if (highAccuracy) {
            dailyNew = 25;
        } else {
            dailyNew = 15;
        }

        if (errorProneCount >= 5) {
            dailyNew = Math.min(dailyNew, 10);
        }

        int dailyReview = (int) Math.max(10, dueCount == 0 ? Math.max(10, masteredCount / 2) : dueCount);
        if (coldStart) {
            dailyReview = Math.max(10, dailyReview);
        } else if (lowAccuracy) {
            dailyReview = Math.max(20, dailyReview);
        }

        // Suggest mode based on current state
        String mode;
        String reason;
        if (errorProneCount >= 5) {
            mode = "context_deep";
            reason = "你有较多反复出错的单词，建议用语境深度学习集中攻克。";
        } else if (dueCount > masteredCount) {
            mode = "unified_review";
            reason = "待复习内容比已掌握内容更多，先做统一复习更稳妥。";
        } else if (coldStart) {
            mode = "quick_memory";
            reason = "你还在建立学习节奏，先从少量快速记忆开始更合适。";
        } else if (lowAccuracy) {
            mode = "quick_memory";
            reason = "最近正确率偏低，先缩小新词量，用快速记忆稳住基础。";
        } else {
            mode = "quick_memory";
            reason = "当前节奏不错，可以用快速记忆继续巩固。";
        }

        // Get weak words (exclude conversation-mastered)
        List<WeakWordDTO> weakWords = binds.stream()
                .filter(b -> b.getConsecutiveErrors() != null && b.getConsecutiveErrors() >= 1)
                .filter(b -> !conversationMasteredWordIds.contains(b.getWordId()))
                .sorted(Comparator.comparing((UserWordBind b) -> b.getDifficulty() != null ? b.getDifficulty() : 0.0).reversed())
                .limit(5)
                .map(b -> {
                    WeakWordDTO dto = new WeakWordDTO();
                    dto.setWordId(b.getWordId());
                    dto.setDifficulty(b.getDifficulty() != null ? b.getDifficulty() : 5.0);
                    dto.setConsecutiveErrors(b.getConsecutiveErrors() != null ? b.getConsecutiveErrors() : 0);
                    return dto;
                })
                .collect(Collectors.toList());

        RecommendResponse resp = new RecommendResponse();
        resp.setDailyNewWords(dailyNew);
        resp.setDailyReviewWords(dailyReview);
        resp.setSuggestedMode(mode);
        resp.setReason(reason);
        resp.setWeakWords(weakWords);
        return resp;
    }

    // --- Progress Alert (Scenario 2) ---

    public ProgressAlertResponse checkProgressAlert(Long userId) {
        ProgressAlertResponse resp = new ProgressAlertResponse();
        resp.setHasAlert(false);

        // Get active exam plan
        ExamPlan plan = examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId).orElse(null);
        if (plan == null || plan.getCreatedAt() == null) {
            return resp;
        }

        long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(
                plan.getCreatedAt().toLocalDate(), java.time.LocalDate.now());
        // Need at least 3 days of data to evaluate progress
        if (daysElapsed < 3 || plan.getEstimatedDays() == null || plan.getEstimatedDays() <= 0) {
            return resp;
        }

        // Parse initial vocabulary from planJson
        int currentVocab = 0;
        try {
            var planData = objectMapper.readTree(plan.getPlanJson());
            currentVocab = planData.path("currentVocab").asInt(0);
        } catch (Exception e) { log.warn("Failed to parse planJson for progress alert", e); }

        int targetVocab = plan.getTargetVocab() != null ? plan.getTargetVocab() : 5000;
        int wordsToLearn = Math.max(1, targetVocab - currentVocab);

        // Expected progress: linear interpolation
        double expectedWords = currentVocab + (wordsToLearn * (double) daysElapsed / plan.getEstimatedDays());
        expectedWords = Math.min(expectedWords, targetVocab);

        // Actual progress: mastered words (stability >= 1.2)
        double actualWords = userWordBindRepository.countMasteredByUserId(userId);

        double progressRatio = expectedWords > 0 ? actualWords / expectedWords : 1.0;

        // Alert if behind by >= 15%
        if (progressRatio < 0.85) {
            resp.setHasAlert(true);
            resp.setExpectedWords(Math.round(expectedWords));
            resp.setActualWords(actualWords);
            resp.setProgressRatio(Math.round(progressRatio * 100.0) / 100.0);

            int behindPercent = (int) Math.round((1 - progressRatio) * 100);
            resp.setMessage(String.format(
                    "你的备考进度落后了 %d%%。计划需要掌握 %d 词，目前实际掌握 %.0f 词，预期应达到 %.0f 词。",
                    behindPercent, targetVocab, actualWords, expectedWords));
            resp.setSuggestedAction("建议每天增加学习时间，或使用语境深度学习模式加快进度。");
        }

        return resp;
    }

    // --- Smart Session Planning (Wave 5) ---

    /**
     * Greedy time-budget planner. Given availableMinutes, allocates a session list
     * by priority: overdue review -> stubborn -> reverse-recall (half-learned) -> new words.
     * Fatigue is estimated from quiz volume over the past 30 minutes (>= 30 records => fatigued).
     * When fatigued, the planner avoids long modes (context_deep) and shortens overall budget.
     */
    public SmartSessionPlanDTO planSmartSession(Long userId, int availableMinutes) {
        // Clamp the input to a sensible range (controller also clamps).
        int budget = Math.max(1, Math.min(60, availableMinutes));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime thirtyMinAgo = now.minusMinutes(30);

        // --- Gather signals ---
        long dueCount = userWordBindRepository.countDueByUserId(userId, now);
        long stubbornCount = userWordBindRepository.countStubbornByUserId(userId);

        // Reverse-recall half-learned pool (cap at 20 to limit query cost)
        long reverseCandidateCount = 0;
        try {
            org.springframework.data.domain.Pageable page = org.springframework.data.domain.PageRequest.of(0, 20);
            reverseCandidateCount = userWordBindRepository.findReverseRecallCandidates(userId, page).size();
        } catch (Exception e) {
            log.debug("reverse-recall candidate lookup failed for userId={}", userId, e);
        }

        // ExamPlan daily quota — drives "new words remaining today"
        int dailyNewQuota = 15;
        int dailyReviewQuota = 30;
        ExamPlan plan = examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId).orElse(null);
        if (plan != null) {
            if (plan.getDailyNewWords() != null && plan.getDailyNewWords() > 0) {
                dailyNewQuota = plan.getDailyNewWords();
            }
            if (plan.getDailyReviewWords() != null && plan.getDailyReviewWords() > 0) {
                dailyReviewQuota = plan.getDailyReviewWords();
            }
        }

        long todayNewWords = quizRecordRepository.countByUserIdSinceAndStudyMode(userId, todayStart, "quick_memory");
        long todayReviewWords = quizRecordRepository.countByUserIdSinceAndStudyMode(userId, todayStart, "unified_review");

        int remainingNew = Math.max(0, dailyNewQuota - (int) todayNewWords);
        int remainingReviewQuota = Math.max(0, dailyReviewQuota - (int) todayReviewWords);

        // Fatigue estimate (volume in past 30 min). >= 30 quizzes => fatigued.
        long recentVolume = quizRecordRepository.countByUserIdSince(userId, thirtyMinAgo);
        boolean fatigueWarning = recentVolume >= 30;

        // --- Time costs (seconds per word, defensive estimates) ---
        final int SEC_QUICK = 30;
        final int SEC_REVIEW = 45;
        final int SEC_REVERSE = 20;
        final int SEC_CONTEXT_DEEP_GROUP = 8 * 60; // 8 min per group of ~10
        final int GROUP_SIZE = 10;

        // If fatigued, reduce the practical budget slightly and force short modes
        int effectiveBudgetSec = budget * 60;
        if (fatigueWarning && effectiveBudgetSec > 5 * 60) {
            effectiveBudgetSec = Math.min(effectiveBudgetSec, 5 * 60);
        }

        List<SessionStep> steps = new ArrayList<>();
        int remainingSec = effectiveBudgetSec;

        // 1. Overdue review words (highest priority)
        if (dueCount > 0 && remainingSec >= SEC_REVIEW) {
            int maxByTime = remainingSec / SEC_REVIEW;
            int wantCount = (int) Math.min(dueCount, Math.max(remainingReviewQuota, 1));
            int reviewCount = Math.min(wantCount, maxByTime);
            // Always do at least 1 review when due exists & we have time for it
            if (reviewCount <= 0 && dueCount > 0) {
                reviewCount = 1;
            }
            if (reviewCount > 0) {
                int seconds = reviewCount * SEC_REVIEW;
                steps.add(buildStep(
                        "unified_review", reviewCount,
                        secondsToMinutes(seconds),
                        reviewCount + " 个词到期，需要立即复习",
                        "/review?source=smart-session&words=" + reviewCount
                ));
                remainingSec -= seconds;
            }
        }

        // 2. Stubborn words — only if we have meaningful time left and NOT fatigued
        // context_deep is the canonical mode for stubborn attack (8 min/group), so we need
        // at least one group's worth of time. If fatigued, skip context_deep entirely.
        if (stubbornCount > 0 && !fatigueWarning && remainingSec >= SEC_CONTEXT_DEEP_GROUP) {
            int groups = Math.min((int) Math.min(stubbornCount, GROUP_SIZE) / GROUP_SIZE,
                    remainingSec / SEC_CONTEXT_DEEP_GROUP);
            int wordCount = Math.min((int) stubbornCount, GROUP_SIZE);
            // Build one group attack regardless if stubborn>=1 and budget allows it
            if (wordCount > 0) {
                int seconds = SEC_CONTEXT_DEEP_GROUP;
                steps.add(buildStep(
                        "context_deep", wordCount,
                        secondsToMinutes(seconds),
                        "有 " + stubbornCount + " 个顽固词，用语境深度学习集中攻克",
                        "/deep-learning?source=smart-session&words=" + wordCount
                ));
                remainingSec -= seconds;
            }
            // Suppress unused groups variable warning
            if (groups < 0) { log.trace("groups={}", groups); }
        }

        // 3. Reverse recall — half-learned pool
        if (reverseCandidateCount > 0 && remainingSec >= SEC_REVERSE) {
            int maxByTime = remainingSec / SEC_REVERSE;
            int wantCount = (int) Math.min(reverseCandidateCount, 10L);
            int rrCount = Math.min(wantCount, maxByTime);
            if (rrCount > 0) {
                int seconds = rrCount * SEC_REVERSE;
                steps.add(buildStep(
                        "reverse_recall", rrCount,
                        secondsToMinutes(seconds),
                        "巩固 " + rrCount + " 个半熟词，用主动召回加深印象",
                        "/study/reverse-recall?source=smart-session&words=" + rrCount
                ));
                remainingSec -= seconds;
            }
        }

        // 4. New words (quick_memory) — fills remaining budget toward daily quota
        if (remainingNew > 0 && remainingSec >= SEC_QUICK) {
            int maxByTime = remainingSec / SEC_QUICK;
            int newCount = Math.min(remainingNew, maxByTime);
            if (newCount > 0) {
                int seconds = newCount * SEC_QUICK;
                String reasonText = remainingNew == dailyNewQuota
                        ? "今日还未学新词，先快速过 " + newCount + " 个"
                        : "今日还差 " + remainingNew + " 个新词达成计划，先做 " + newCount + " 个";
                steps.add(buildStep(
                        "quick_memory", newCount,
                        secondsToMinutes(seconds),
                        reasonText,
                        "/quick-memory?source=smart-session&words=" + newCount
                ));
                remainingSec -= seconds;
            }
        }

        // Fallback: nothing scheduled — propose a tiny quick_memory warmup
        if (steps.isEmpty()) {
            int newCount = Math.min(5, Math.max(1, effectiveBudgetSec / SEC_QUICK));
            int seconds = newCount * SEC_QUICK;
            steps.add(buildStep(
                    "quick_memory", newCount,
                    secondsToMinutes(seconds),
                    "暂时没有紧急任务，先做 " + newCount + " 个新词热身",
                    "/quick-memory?source=smart-session&words=" + newCount
            ));
        }

        int totalEstMinutes = steps.stream().mapToInt(SessionStep::getEstimatedMinutes).sum();

        SmartSessionPlanDTO dto = new SmartSessionPlanDTO();
        dto.setAvailableMinutes(budget);
        dto.setTotalEstimatedMinutes(totalEstMinutes);
        dto.setFatigueWarning(fatigueWarning);
        dto.setSteps(steps);
        dto.setSummary(buildSmartSessionSummary(budget, fatigueWarning, steps));
        return dto;
    }

    // --- Realtime Nudge (Wave 6 — Feature A) ---

    /**
     * 实时介入：查最近 5 分钟该用户在指定 studyMode 下的失败记录（grade &lt; 3.0）。
     * 如果 &gt;= 3 条失败，按词尾 / 长度 找规律并返回一句 TiMo 话术。
     * 没有规律或失败数不足时返回 Optional.empty()。
     */
    public Optional<RealtimeNudgeDTO> evaluateRealtimeNudge(Long userId, String studyMode) {
        if (userId == null) {
            return Optional.empty();
        }
        LocalDateTime fiveMinAgo = LocalDateTime.now().minusMinutes(5);
        LocalDateTime now = LocalDateTime.now();
        List<QuizRecord> recent;
        try {
            recent = quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, fiveMinAgo, now);
        } catch (Exception e) {
            log.debug("evaluateRealtimeNudge: query failed for userId={}", userId, e);
            return Optional.empty();
        }
        if (recent == null || recent.isEmpty()) {
            return Optional.empty();
        }

        // 过滤：失败 + 模式匹配（studyMode 为空或 null 时不过滤模式）
        boolean modeFilter = studyMode != null && !studyMode.isBlank();
        List<QuizRecord> failures = recent.stream()
                .filter(r -> r.getGrade() != null && r.getGrade() < 3.0)
                .filter(r -> !modeFilter || studyMode.equals(r.getStudyMode()))
                .sorted(Comparator.comparing(QuizRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (failures.size() < 3) {
            return Optional.empty();
        }

        // 取最近 3 条失败的 wordId，拉对应的 Word
        List<Long> wordIds = failures.stream()
                .map(QuizRecord::getWordId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .toList();
        if (wordIds.size() < 3) {
            return Optional.empty();
        }
        List<Word> words = wordRepository.findByIdIn(wordIds);
        if (words == null || words.size() < 3) {
            return Optional.empty();
        }
        List<String> rawWords = words.stream()
                .map(Word::getWord)
                .filter(s -> s != null && !s.isBlank())
                .map(String::toLowerCase)
                .toList();
        if (rawWords.size() < 3) {
            return Optional.empty();
        }

        // 1) 词尾规律检测
        String suffix = detectCommonSuffix(rawWords);
        if (suffix != null) {
            RealtimeNudgeDTO dto = new RealtimeNudgeDTO();
            dto.setPattern(suffix + " 后缀错误");
            dto.setMessage("我注意到你最近 3 个词都在 " + suffix + " 后缀上栽跟头，要不要看个小专题？");
            dto.setSuggestedAction("open_chat_topic");
            dto.setSuggestedRoute("/agent");
            return Optional.of(dto);
        }

        // 2) 长单词疲劳检测（全部 >= 8 字母）
        boolean allLong = rawWords.stream().allMatch(w -> w.length() >= 8);
        if (allLong) {
            RealtimeNudgeDTO dto = new RealtimeNudgeDTO();
            dto.setPattern("长单词疲劳");
            dto.setMessage("最近这几个长单词让你受挫了，要不切换到闪电模式过几个短词热身？");
            dto.setSuggestedAction("switch_to_quick_memory");
            dto.setSuggestedRoute("/quick-memory");
            return Optional.of(dto);
        }

        return Optional.empty();
    }

    /**
     * 返回 3 个词共有的、长度 &gt;= 3 的最长后缀（限定常见词尾如 -tion/-ous/-ful/-ing/-ment/-ity/-able/-ness/-ate/-ive/-ly）。
     * 没找到返回 null。
     */
    private String detectCommonSuffix(List<String> lowerWords) {
        // 优先匹配预定义的常见词尾，按长度倒序检测
        String[] commonSuffixes = {
                "tion", "sion", "ment", "ness", "able", "ible", "ous", "ful", "ing", "ity", "ive", "ate", "ly"
        };
        for (String suf : commonSuffixes) {
            boolean allMatch = lowerWords.stream()
                    .allMatch(w -> w.length() > suf.length() && w.endsWith(suf));
            if (allMatch) {
                return "-" + suf;
            }
        }
        return null;
    }

    private SessionStep buildStep(String mode, int wordCount, int estimatedMinutes, String reason, String navigationUrl) {
        SessionStep s = new SessionStep();
        s.setMode(mode);
        s.setWordCount(wordCount);
        s.setEstimatedMinutes(estimatedMinutes);
        s.setReason(reason);
        s.setNavigationUrl(navigationUrl);
        return s;
    }

    private int secondsToMinutes(int seconds) {
        return Math.max(1, (int) Math.round(seconds / 60.0));
    }

    private String buildSmartSessionSummary(int budget, boolean fatigueWarning, List<SessionStep> steps) {
        if (steps.isEmpty()) {
            return budget + " 分钟方案：暂无可安排任务。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(budget).append(" 分钟方案：");
        for (int i = 0; i < steps.size(); i++) {
            SessionStep s = steps.get(i);
            if (i > 0) sb.append("，");
            sb.append(modeLabel(s.getMode())).append(' ').append(s.getWordCount()).append(" 词");
        }
        sb.append("。");
        if (fatigueWarning) {
            sb.append("（最近练习较多，已为你切到短模式）");
        }
        return sb.toString();
    }

    private String modeLabel(String mode) {
        return switch (mode) {
            case "quick_memory" -> "快速记忆";
            case "context_deep" -> "语境深度";
            case "unified_review" -> "统一复习";
            case "reverse_recall" -> "中→英召回";
            default -> mode;
        };
    }

    // --- Analyze Word ---

    public AnalyzeWordResponse analyzeWord(String word) {
        Word dbWord = wordRepository.findFirstByWordIgnoreCase(word).orElse(null);
        List<Meaning> meanings = dbWord != null ? meaningRepository.findByWordIdOrderBySortOrder(dbWord.getId()) : List.of();
        List<Example> examples = dbWord != null ? exampleRepository.findByWordIdOrderByIdAsc(dbWord.getId()) : List.of();
        String wordContext = buildWordContext(dbWord, meanings, examples);

        String prompt = """
                请对英语单词 "%s" 进行深度分析。

                词库已知信息：
                %s

                要求：
                1. etymology: 词根词缀拆解分析，帮助理解单词构成
                2. mnemonic: 助记技巧，可以是谐音、联想、拆分等
                3. usage: 常见搭配和用法说明
                4. synonyms: 近义词辨析，说明它们之间的区别
                5. example: 一个地道的例句（英文），并附中文翻译

                严格按照以下JSON格式返回，不要添加任何其他内容：

                {
                  "phonetic": "音标",
                  "meaning": "核心词义",
                  "etymology": "词根词缀分析",
                  "mnemonic": "助记技巧",
                  "usage": "常见搭配和用法",
                  "synonyms": "近义词辨析",
                  "example": "英文例句 — 中文翻译"
                }
                """.formatted(word, wordContext);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        String reply;
        try {
            reply = deepSeekClient.chat(messages);
        } catch (Exception e) {
            log.error("analyzeWord DeepSeek call failed for word={}", word, e);
            reply = null;
        }

        if (reply != null) {
            try {
                JsonNode node = objectMapper.readTree(reply);
                AnalyzeWordResponse resp = new AnalyzeWordResponse();
                resp.setPhonetic(firstNonBlank(node.path("phonetic").asText(""), dbWord != null ? dbWord.getPhonetic() : ""));
                resp.setMeaning(firstNonBlank(node.path("meaning").asText(""), firstMeaningText(meanings)));
                resp.setEtymology(node.path("etymology").asText(""));
                resp.setMnemonic(node.path("mnemonic").asText(""));
                resp.setUsage(node.path("usage").asText(""));
                resp.setSynonyms(node.path("synonyms").asText(""));
                resp.setExample(firstNonBlank(node.path("example").asText(""), buildExampleText(examples, word)));
                return resp;
            } catch (Exception e) {
                log.warn("Failed to parse analyzeWord JSON for word={}", word, e);
            }
        }

        log.warn("Using fallback analysis for word={} (DeepSeek unavailable)", word);
        return buildFallbackAnalysis(word, dbWord, meanings, examples);
    }

    private AnalyzeWordResponse buildFallbackAnalysis(String word, Word dbWord, List<Meaning> meanings, List<Example> examples) {
        AnalyzeWordResponse resp = new AnalyzeWordResponse();
        resp.setPhonetic(dbWord != null ? dbWord.getPhonetic() : "");
        resp.setMeaning(firstMeaningText(meanings));
        resp.setEtymology("暂无词根词缀分析，请结合词义和例句记忆。");
        resp.setMnemonic(buildFallbackMnemonic(word, firstMeaningText(meanings)));
        resp.setUsage(buildFallbackUsage(meanings, word));
        resp.setSynonyms("暂无近义词辨析，可先结合上下文理解这个词的使用场景。");
        resp.setExample(buildExampleText(examples, word));
        return resp;
    }

    private String buildWordContext(Word dbWord, List<Meaning> meanings, List<Example> examples) {
        StringBuilder sb = new StringBuilder();
        if (dbWord != null) {
            if (dbWord.getPhonetic() != null && !dbWord.getPhonetic().isBlank()) {
                sb.append("- 音标：").append(dbWord.getPhonetic()).append('\n');
            }
            if (dbWord.getPos() != null && !dbWord.getPos().isBlank()) {
                sb.append("- 词性：").append(dbWord.getPos()).append('\n');
            }
        }
        if (meanings != null && !meanings.isEmpty()) {
            String meaningText = meanings.stream()
                    .map(m -> firstNonBlank(m.getPartOfSpeech(), "") + " " + firstNonBlank(m.getMeaning(), ""))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("；"));
            if (!meaningText.isBlank()) {
                sb.append("- 释义：").append(meaningText).append('\n');
            }
        }
        if (examples != null && !examples.isEmpty()) {
            Example example = examples.get(0);
            String exampleText = firstNonBlank(example.getSentence(), "");
            if (!exampleText.isBlank()) {
                sb.append("- 例句：").append(exampleText);
                if (example.getTranslation() != null && !example.getTranslation().isBlank()) {
                    sb.append(" / ").append(example.getTranslation());
                }
                sb.append('\n');
            }
        }
        return sb.length() == 0 ? "- 暂无词库上下文" : sb.toString().trim();
    }

    private String firstMeaningText(List<Meaning> meanings) {
        if (meanings == null || meanings.isEmpty()) {
            return "";
        }
        return meanings.stream()
                .map(Meaning::getMeaning)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");
    }

    private String buildFallbackMnemonic(String word, String meaning) {
        if (meaning != null && !meaning.isBlank()) {
            return "先把 " + word + " 和意思“" + meaning + "”绑在一起记，再用例句复述一遍。";
        }
        return "把 " + word + " 放进一句你熟悉的话里，先记住使用场景再记拼写。";
    }

    private String buildFallbackUsage(List<Meaning> meanings, String word) {
        List<Meaning> safeMeanings = meanings == null ? List.of() : meanings;
        String pos = safeMeanings.stream()
                .map(Meaning::getPartOfSpeech)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("这个词");
        String meaning = firstMeaningText(safeMeanings);
        if (meaning.isBlank()) {
            return "可以先在阅读和例句中观察 “" + word + "” 的实际用法。";
        }
        return pos + " 场景里常见，先用它的核心意思理解，再扩展到搭配。";
    }

    private String buildExampleText(List<Example> examples, String word) {
        if (examples != null && !examples.isEmpty()) {
            Example example = examples.get(0);
            if (example.getTranslation() != null && !example.getTranslation().isBlank()) {
                return example.getSentence() + " — " + example.getTranslation();
            }
            if (example.getSentence() != null && !example.getSentence().isBlank()) {
                return example.getSentence();
            }
        }
        return "\"" + word + "\" is used here in a simple learning example. — 这里用 " + word + " 做一个简单示例。";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    // --- Weekly Report ---

    public WeeklyReportResponse getWeeklyReport(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(6).toLocalDate().atStartOfDay();
        LocalDateTime prevWeekAgo = now.minusDays(13).toLocalDate().atStartOfDay();
        LocalDateTime prevWeekEnd = weekAgo.minusNanos(1);

        List<com.timo.words.modules.study.entity.QuizRecord> weekRecords =
                quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, weekAgo, now);
        List<com.timo.words.modules.study.entity.QuizRecord> prevWeekRecords =
                quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, prevWeekAgo, prevWeekEnd);

        int totalStudied = weekRecords.size();
        long newWords = quizRecordRepository.countDistinctWordIdByUserIdSince(userId, weekAgo);
        int reviewsCompleted = totalStudied;
        long masteredWords = userWordBindRepository.countMasteredByUserId(userId);

        double avgAccuracy = 0;
        if (totalStudied > 0) {
            long correct = weekRecords.stream()
                    .filter(r -> r.getGrade() != null && r.getGrade() >= 3.0).count();
            avgAccuracy = Math.round(correct * 1000.0 / totalStudied) / 10.0;
        }

        double prevAccuracy = 0;
        if (!prevWeekRecords.isEmpty()) {
            long prevCorrect = prevWeekRecords.stream()
                    .filter(r -> r.getGrade() != null && r.getGrade() >= 3.0).count();
            prevAccuracy = Math.round(prevCorrect * 1000.0 / prevWeekRecords.size()) / 10.0;
        }

        long studyDays = quizRecordRepository.countStudyDaysSince(userId, weekAgo);

        // Calculate longest streak from all checkin records
        List<com.timo.words.modules.calendar.entity.CheckinRecord> checkins =
                checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(userId);
        int longestStreak = calculateStreak(checkins);

        // Build suggestions based on data
        List<String> suggestions = new ArrayList<>();
        if (studyDays < 5) {
            suggestions.add("本周学习" + studyDays + "天，建议保持每天学习的习惯");
        }
        if (avgAccuracy < 60) {
            suggestions.add("正确率偏低(" + (int) avgAccuracy + "%)，建议先用快速记忆巩固基础");
        }
        if (totalStudied == 0) {
            suggestions.add("暂无学习数据，开始你的第一课吧！");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("学习状态良好，继续保持！");
        }

        List<String> insights = new ArrayList<>();
        if (totalStudied == 0) {
            insights.add("本周还没有学习记录，先从 5 个词开始更容易进入状态。");
        } else {
            insights.add("本周共学习 " + totalStudied + " 个词，平均正确率 " + (int) avgAccuracy + "%。");
            if (prevWeekRecords.isEmpty()) {
                insights.add("这是一个新的对比周期，建议继续保持这个节奏。");
            } else {
                double delta = Math.round((avgAccuracy - prevAccuracy) * 10.0) / 10.0;
                if (delta > 0) {
                    insights.add("正确率比上周提升了 " + delta + " 个百分点。");
                } else if (delta < 0) {
                    insights.add("正确率比上周下降了 " + Math.abs(delta) + " 个百分点，需要稍微收紧复习节奏。");
                } else {
                    insights.add("正确率和上周基本持平，可以继续稳定输出。");
                }
            }
            if (masteredWords > 0) {
                insights.add("当前已掌握 " + masteredWords + " 个词，可以把更多精力放在顽固词上。");
            }
        }

        String weakness;
        String suggestion;
        if (totalStudied == 0) {
            weakness = "暂无学习数据。";
            suggestion = "先完成一轮 5 个词的快速记忆，建立学习起点。";
        } else if (avgAccuracy < 60) {
            weakness = "本周正确率偏低，说明基础还不稳。";
            suggestion = "优先回顾错词，再用语境深度学习巩固记忆。";
        } else if (studyDays < 4) {
            weakness = "学习天数偏少，节奏还有提升空间。";
            suggestion = "尽量把学习拆成更小的日任务，保持连续性。";
        } else {
            weakness = "整体学习状态比较稳定。";
            suggestion = "继续保持当前节奏，重点关注顽固词和临界遗忘词。";
        }

        WeeklyReportResponse resp = new WeeklyReportResponse();
        resp.setTotalStudied(totalStudied);
        resp.setTotalWords(totalStudied);
        resp.setNewWordsLearned((int) newWords);
        resp.setReviewsCompleted(reviewsCompleted);
        resp.setMasteredWords((int) masteredWords);
        resp.setAvgAccuracy(avgAccuracy);
        resp.setAccuracyDelta(prevWeekRecords.isEmpty() ? 0 : Math.round((avgAccuracy - prevAccuracy) * 10.0) / 10.0);
        resp.setStudyDays((int) studyDays);
        resp.setLongestStreak(longestStreak);
        resp.setSummary(String.format("本周学习%d天，共学%d个单词，正确率%.0f%%",
                studyDays, newWords, avgAccuracy));
        resp.setWeakness(weakness);
        resp.setSuggestion(suggestion);
        resp.setInsights(insights);
        resp.setSuggestions(suggestions);
        // Wave 6 — Feature B: 时段偏好分析（数据不足时返回 null）
        resp.setTimeAnalysis(buildTimeOfDayAnalysis(weekRecords));
        return resp;
    }

    /**
     * 按小时分桶统计正确率，找出最高/最低正确率小时（每桶至少 5 条），再用 5 分钟间隔切分会话计算平均长度。
     * 任何一项数据不足时直接返回 null（向后兼容老前端字段）。
     */
    TimeOfDayAnalysis buildTimeOfDayAnalysis(List<QuizRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        // 24 桶：每桶 [总数, 正确数]
        int[] totals = new int[24];
        int[] corrects = new int[24];
        List<LocalDateTime> timestamps = new ArrayList<>();
        for (QuizRecord r : records) {
            if (r.getCreatedAt() == null || r.getGrade() == null) {
                continue;
            }
            int hour = r.getCreatedAt().getHour();
            totals[hour]++;
            if (r.getGrade() >= 3.0) {
                corrects[hour]++;
            }
            timestamps.add(r.getCreatedAt());
        }

        // 找出每桶 >= 5 条的所有小时
        int bestHour = -1, worstHour = -1;
        double bestAcc = -1.0, worstAcc = 2.0;
        for (int h = 0; h < 24; h++) {
            if (totals[h] < 5) continue;
            double acc = (double) corrects[h] / totals[h];
            if (acc > bestAcc) {
                bestAcc = acc;
                bestHour = h;
            }
            if (acc < worstAcc) {
                worstAcc = acc;
                worstHour = h;
            }
        }

        if (bestHour < 0) {
            return null;  // 没有任何小时累计 >= 5 条
        }

        // 计算平均会话长度：按 5 分钟间隔切分
        int avgSessionMinutes = computeAverageSessionMinutes(timestamps);

        TimeOfDayAnalysis dto = new TimeOfDayAnalysis();
        dto.setBestHourRange(formatHourRange(bestHour));
        dto.setBestHourAccuracy(Math.round(bestAcc * 100.0) / 100.0);
        if (worstHour >= 0 && worstHour != bestHour) {
            dto.setWorstHourRange(formatHourRange(worstHour));
            dto.setWorstHourAccuracy(Math.round(worstAcc * 100.0) / 100.0);
        }
        dto.setAvgSessionLengthMinutes(avgSessionMinutes);
        dto.setRecommendation(buildTimeRecommendation(bestHour, bestAcc, worstHour, worstAcc, avgSessionMinutes));
        return dto;
    }

    private String formatHourRange(int hour) {
        int next = (hour + 1) % 24;
        return String.format("%d:00-%d:00", hour, next);
    }

    private int computeAverageSessionMinutes(List<LocalDateTime> timestamps) {
        if (timestamps == null || timestamps.size() < 2) {
            return 0;
        }
        List<LocalDateTime> sorted = new ArrayList<>(timestamps);
        sorted.sort(Comparator.naturalOrder());

        List<Long> sessionLengthsSec = new ArrayList<>();
        LocalDateTime sessionStart = sorted.get(0);
        LocalDateTime lastTs = sessionStart;
        for (int i = 1; i < sorted.size(); i++) {
            LocalDateTime cur = sorted.get(i);
            long gapSec = java.time.Duration.between(lastTs, cur).getSeconds();
            if (gapSec > 300) {
                // 旧会话结束
                sessionLengthsSec.add(java.time.Duration.between(sessionStart, lastTs).getSeconds());
                sessionStart = cur;
            }
            lastTs = cur;
        }
        sessionLengthsSec.add(java.time.Duration.between(sessionStart, lastTs).getSeconds());

        long totalSec = sessionLengthsSec.stream().mapToLong(Long::longValue).sum();
        long avgSec = totalSec / sessionLengthsSec.size();
        return Math.max(0, (int) Math.round(avgSec / 60.0));
    }

    private String buildTimeRecommendation(int bestHour, double bestAcc, int worstHour, double worstAcc, int avgSessionMin) {
        StringBuilder sb = new StringBuilder();
        sb.append("你在 ").append(formatHourRange(bestHour))
          .append(" 正确率最高（").append(Math.round(bestAcc * 100)).append("%），建议把难词安排在这个时段。");
        if (worstHour >= 0 && worstHour != bestHour && worstAcc + 0.05 < bestAcc) {
            sb.append("相比之下，").append(formatHourRange(worstHour))
              .append(" 正确率偏低，可以放轻量复习。");
        }
        if (avgSessionMin >= 25) {
            sb.append("你的平均会话约 ").append(avgSessionMin).append(" 分钟，可以考虑中途短休一次。");
        }
        return sb.toString();
    }

    // --- Session Report (Plan C: Coach Summary) ---

    public SessionReportResponse generateSessionReport(SessionReportRequest req) {
        SessionReportResponse resp = new SessionReportResponse();
        String modeLabel = switch (req.getStudyMode()) {
            case "quick_memory" -> "快速记忆";
            case "context_deep" -> "语境深度学习";
            case "unified_review" -> "统一复习";
            default -> "学习";
        };

        int accuracy = req.getTotalWords() > 0
                ? (int) Math.round(req.getCorrectCount() * 100.0 / req.getTotalWords())
                : 0;

        // Try DeepSeek first
        String prompt = """
                你是TiMo，一个友善的英语备考教练。用户刚刚完成了一次%s学习，请给出简短的鼓励和点评。

                学习数据：
                - 总单词数：%d
                - 正确数：%d
                - 错误数：%d
                - 正确率：%d%%
                - 用时：%d秒
                - 错词：%s

                要求：
                1. 用自然口语化的语气，20-40字以内
                 2. 如果正确率高（>=80%%），给予表扬
                 3. 如果正确率低（<60%%），给予鼓励并建议复习
                4. 不要提JSON格式，只需返回纯文字
                """.formatted(
                        modeLabel,
                        req.getTotalWords(), req.getCorrectCount(), req.getWrongCount(),
                        accuracy,
                        req.getElapsedMs() / 1000,
                        req.getWrongWordTexts() != null && !req.getWrongWordTexts().isEmpty()
                                ? String.join("、", req.getWrongWordTexts())
                                : "无");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        String summary;
        try {
            summary = deepSeekClient.chatFreeForm(messages);
        } catch (Exception e) {
            log.error("DeepSeek session report failed", e);
            summary = null;
        }

        if (summary == null || summary.isBlank()) {
            summary = generateFallbackSummary(req, accuracy);
        } else {
            // Trim any JSON artifacts
            int jsonStart = summary.lastIndexOf('{');
            if (jsonStart >= 0) {
                summary = summary.substring(0, jsonStart).trim();
            }
            if (summary.isEmpty()) {
                summary = generateFallbackSummary(req, accuracy);
            }
        }

        resp.setSummary(summary);
        resp.setTiMoState(accuracy >= 80 ? "success" : "idle");

        List<String> actions = new ArrayList<>();
        if (req.getWrongCount() > 0) {
            actions.add("开始今日学习");
        }
        resp.setActions(actions);

        return resp;
    }

    private String generateFallbackSummary(SessionReportRequest req, int accuracy) {
        if (accuracy >= 80) {
            return "太棒了！正确率 " + accuracy + "%，今天状态不错，继续保持！";
        } else if (accuracy >= 60) {
            return "还不错，正确率 " + accuracy + "%，再接再厉！";
        } else {
            return "正确率 " + accuracy + "%，这几个词需要再巩固一下。错词已自动加入复习队列，明天记得来复习哦！";
        }
    }

    private int calculateStreak(List<com.timo.words.modules.calendar.entity.CheckinRecord> checkins) {
        if (checkins.isEmpty()) return 0;
        java.time.LocalDate expected = java.time.LocalDate.now();
        int streak = 0;
        for (var c : checkins) {
            if (c.getCheckinDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (c.getCheckinDate().isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    // --- Generate Passage ---

    public PassageResponse generatePassage(PassageRequest req) {
        List<String> words = req.getWords();
        String wordList = String.join(", ", words);

        String prompt = """
                You are an expert English writer and educator. Write an engaging, coherent short story (120-180 words) that naturally embeds ALL of the following target words: %s.

                IMPORTANT RULES:
                1. Build a meaningful storyline FIRST, then weave the target words into it naturally. Do NOT force words into unrelated sentences.
                2. Each target word must appear EXACTLY as given (do not change form: no adding -s, -ed, -ing, etc.).
                3. The story must be grammatically correct, logically coherent, and at college English level.
                4. The story should be interesting and meaningful, not a random collection of sentences.
                5. Word count: between 120 and 180 words (excluding the wordSentences section).

                After the story, provide a separate example sentence for each target word.

                Return ONLY valid JSON in this exact format:
                {
                  "title": "A creative title for the story",
                  "passage": "The complete 120-180 word story with all target words embedded naturally",
                  "translation": "Brief Chinese translation of the story (2-3 sentences summary)",
                  "wordSentences": {
                    "word1": "A natural example sentence using word1",
                    "word2": "A natural example sentence using word2"
                  }
                }
                """.formatted(wordList);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        String reply;
        try {
            reply = deepSeekClient.chat(messages);
        } catch (Exception e) {
            log.error("generatePassage DeepSeek call failed", e);
            reply = null;
        }

        if (reply != null) {
            try {
                JsonNode node = objectMapper.readTree(reply);
                String passage = node.path("passage").asText("");

                // Quality check: verify all words appear in passage
                String lowerPassage = passage.toLowerCase();
                boolean allWordsPresent = words.stream()
                        .allMatch(w -> lowerPassage.contains(w.toLowerCase()));
                int wordCount = passage.trim().isEmpty() ? 0 : passage.trim().split("\\s+").length;

                if (!allWordsPresent || wordCount < 120 || wordCount > 180) {
                    log.warn("Passage quality check failed: wordsPresent={}, wordCount={}. Retrying with stricter prompt...", allWordsPresent, wordCount);
                    return generatePassageWithRetry(words, wordList);
                }

                PassageResponse resp = new PassageResponse();
                resp.setTitle(node.path("title").asText(""));
                resp.setPassage(passage);
                resp.setTranslation(node.path("translation").asText(""));

                Map<String, String> wordSentences = new HashMap<>();
                JsonNode wsNode = node.path("wordSentences");
                if (wsNode.isObject()) {
                    wsNode.fields().forEachRemaining(entry ->
                            wordSentences.put(entry.getKey(), entry.getValue().asText()));
                }
                resp.setWordSentences(wordSentences);
                return resp;
            } catch (Exception e) {
                log.warn("Failed to parse passage JSON, using fallback", e);
            }
        }

        log.warn("Using fallback passage for words={} (DeepSeek unavailable)", words);
        return buildFallbackPassage(words);
    }

    private PassageResponse generatePassageWithRetry(List<String> words, String wordList) {
        String retryPrompt = """
                You are an expert English writer. Create one coherent story of 120-180 words.
                The story must include ALL of these exact words once: %s.

                Critical rules:
                - Build a real story first. Do not concatenate unrelated sentences.
                - Keep every target word exactly as written. Do not change tense or form.
                - Grammar must be natural and correct.
                - Return ONLY valid JSON.

                JSON format:
                {"title":"...","passage":"...","translation":"...","wordSentences":{"word1":"...","word2":"..."}}
                """.formatted(wordList);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", retryPrompt));

        try {
            String reply = deepSeekClient.chat(messages);
            if (reply != null) {
                JsonNode node = objectMapper.readTree(reply);
                PassageResponse resp = new PassageResponse();
                resp.setTitle(node.path("title").asText(""));
                resp.setPassage(node.path("passage").asText(""));
                resp.setTranslation(node.path("translation").asText(""));
                Map<String, String> wordSentences = new HashMap<>();
                JsonNode wsNode = node.path("wordSentences");
                if (wsNode.isObject()) {
                    wsNode.fields().forEachRemaining(entry ->
                            wordSentences.put(entry.getKey(), entry.getValue().asText()));
                }
                resp.setWordSentences(wordSentences);
                return resp;
            }
        } catch (Exception e) {
            log.error("Retry passage generation failed", e);
        }
        return buildFallbackPassage(words);
    }

    private PassageResponse buildFallbackPassage(List<String> words) {
        List<Word> dbWords = wordRepository.findByWordIn(words.stream()
                .map(String::toLowerCase)
                .toList());
        Map<String, Word> wordMap = dbWords.stream()
                .collect(Collectors.toMap(w -> w.getWord().toLowerCase(), w -> w, (a, b) -> a));

        String title = words.isEmpty() ? "A Small Learning Moment" : "A Small Learning Journey";
        StringBuilder story = new StringBuilder();
        story.append("On a quiet Sunday morning, Maya decided to review a small set of new words before meeting her friend at the library. ");
        story.append("She wanted to stay focused, even when the weather was tempting and the coffee shop looked more relaxing than her notebook. ");
        story.append("As she studied, she tried to connect each word to a real situation, so the meaning would feel vivid instead of abstract. ");
        story.append("Later, she told her friend that learning vocabulary was easier when every word lived inside a story rather than a lonely list. ");

        Map<String, String> wordSentences = new LinkedHashMap<>();
        for (String word : words) {
            Word dbWord = wordMap.get(word.toLowerCase());
            String sentence = buildWordSentence(word, dbWord);
            story.append(' ').append(sentence);
            wordSentences.put(word, sentence);
        }

        PassageResponse resp = new PassageResponse();
        resp.setTitle(title);
        resp.setPassage(story.toString());
        resp.setTranslation(buildFallbackTranslation(words));
        resp.setWordSentences(wordSentences);
        return resp;
    }

    private String buildWordSentence(String word, Word dbWord) {
        String meaning = getPrimaryMeaning(dbWord);
        String sentence;
        if (meaning.contains("放弃")) {
            sentence = String.format("She refused to %s her goal, even when the task felt difficult.", word);
        } else if (meaning.contains("模糊") || meaning.contains("不明确")) {
            sentence = String.format("The instructions were still %s, so the team asked for a clearer explanation.", word);
        } else if (meaning.contains("善良") || meaning.contains("仁慈")) {
            sentence = String.format("Her %s reply made everyone in the room feel calm and welcome.", word);
        } else if (meaning.contains("离开") || meaning.contains("抛弃")) {
            sentence = String.format("He decided not to %s the project after he saw how much progress they had made.", word);
        } else if (meaning.contains("重要") || meaning.contains("关键")) {
            sentence = String.format("That idea became a %s of the whole plan.", word);
        } else {
            sentence = String.format("She used the word %s naturally while talking about her day.", word);
        }
        return sentence;
    }

    private String getPrimaryMeaning(Word word) {
        if (word == null || word.getMeanings() == null || word.getMeanings().isEmpty()) {
            return "";
        }
        return word.getMeanings().get(0).getMeaning() == null ? "" : word.getMeanings().get(0).getMeaning();
    }

    private String buildFallbackTranslation(List<String> words) {
        if (words.isEmpty()) {
            return "这是一段用于学习的短故事。";
        }
        return "这段短文围绕 " + String.join("、", words) + " 展开，帮助你在语境中理解和记忆这些单词。";
    }

    // --- Generate Questions ---

    public Map<String, Object> generateQuestions(List<String> words) {
        String wordList = String.join(", ", words);

        String prompt = """
                请为以下单词生成选择题：%s

                要求：
                1. 每个单词生成1道四选一的中文释义选择题
                2. 严格按照以下JSON格式返回，不要添加任何其他内容：

                {
                  "questions": [
                    {
                      "word": "单词",
                      "question": "单词的中文释义是什么？",
                      "options": ["选项A", "选项B", "选项C", "选项D"],
                      "correctIndex": 0,
                      "explanation": "解析说明"
                    }
                  ]
                }
                """.formatted(wordList);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        String reply;
        try {
            reply = deepSeekClient.chat(messages);
        } catch (Exception e) {
            log.error("generateQuestions DeepSeek call failed", e);
            reply = null;
        }

        if (reply != null) {
            try {
                JsonNode node = objectMapper.readTree(reply);
                Map<String, Object> result = new LinkedHashMap<>();
                List<Map<String, Object>> questions = new ArrayList<>();
                JsonNode qArray = node.path("questions");
                if (qArray.isArray()) {
                    for (JsonNode q : qArray) {
                        Map<String, Object> question = new LinkedHashMap<>();
                        question.put("word", q.path("word").asText());
                        question.put("question", q.path("question").asText());
                        question.put("options", objectMapper.convertValue(q.path("options"), List.class));
                        question.put("correctIndex", q.path("correctIndex").asInt());
                        question.put("explanation", q.path("explanation").asText());
                        questions.add(question);
                    }
                }
                result.put("questions", questions);
                return result;
            } catch (Exception e) {
                log.warn("Failed to parse questions JSON", e);
            }
        }

        log.warn("Using fallback questions for words={} (DeepSeek unavailable)", words);
        Map<String, Object> fallback = new LinkedHashMap<>();
        List<Map<String, Object>> fallbackQuestions = new ArrayList<>();
        List<Word> dbWords = wordRepository.findByWordIn(words);
        Map<String, Word> wordMap = new LinkedHashMap<>();
        for (Word w : dbWords) { wordMap.put(w.getWord().toLowerCase(), w); }

        // Collect all available meanings for distractors
        List<String> allMeanings = new ArrayList<>();
        for (Word w : dbWords) {
            List<Meaning> mgs = meaningRepository.findByWordIdOrderBySortOrder(w.getId());
            for (Meaning m : mgs) {
                if (m.getMeaning() != null && !m.getMeaning().isBlank()) {
                    allMeanings.add(m.getMeaning());
                }
            }
        }

        for (String word : words) {
            Word dbWord = wordMap.get(word.toLowerCase());
            String correctMeaning = "";
            List<String> options = new ArrayList<>();
            if (dbWord != null) {
                List<Meaning> mgs = meaningRepository.findByWordIdOrderBySortOrder(dbWord.getId());
                if (!mgs.isEmpty() && mgs.get(0).getMeaning() != null) {
                    correctMeaning = mgs.get(0).getMeaning();
                }
            }
            if (correctMeaning.isBlank()) {
                correctMeaning = "（暂无释义）";
            }
            options.add(correctMeaning);
            final String correct = correctMeaning;
            // Pick up to 3 distractors from other words' meanings
            List<String> distractors = allMeanings.stream()
                    .filter(m -> !m.equals(correct))
                    .distinct()
                    .limit(3)
                    .toList();
            options.addAll(distractors);
            // Pad if still < 4
            while (options.size() < 4) { options.add("选项" + (char)('A' + options.size())); }

            Map<String, Object> q = new LinkedHashMap<>();
            q.put("word", word);
            q.put("question", "请选择 " + word + " 的正确释义");
            q.put("options", options);
            q.put("correctIndex", 0);
            q.put("explanation", correctMeaning);
            fallbackQuestions.add(q);
        }
        fallback.put("questions", fallbackQuestions);
        return fallback;
    }

    // --- Helpers ---

    private String buildUserInfo(User user) {
        if (user == null) return "新用户";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("用户称呼：%s，考试类型：%s",
                user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : "同学",
                user.getExamType() != null ? user.getExamType() : "未设置"));

        // Inject learning statistics
        try {
            Long userId = user.getId();
            long totalWords = userWordBindRepository.countByUserId(userId);
            long masteredWords = userWordBindRepository.countMasteredByUserId(userId);
            long dueCount = userWordBindRepository.countDueByUserId(userId, LocalDateTime.now());
            long stubbornCount = userWordBindRepository.countStubbornByUserId(userId);

            // Recent accuracy (last 50 quiz records)
            LocalDateTime weekAgo = LocalDate.now().minusDays(7).atStartOfDay();
            List<QuizRecord> recentRecords = quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, weekAgo, LocalDateTime.now());
            double recentAccuracy = 0;
            if (!recentRecords.isEmpty()) {
                long correct = recentRecords.stream().filter(r -> r.getGrade() != null && r.getGrade() >= 3.0).count();
                recentAccuracy = Math.round(correct * 1000.0 / recentRecords.size()) / 10.0;
            }

            // Check-in streak
            List<CheckinRecord> checkins = checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(userId);
            int streak = calculateStreak(checkins);

            sb.append(String.format(
                    "，已学单词%d个（已掌握%d个），待复习%d个，顽固词%d个，近7天正确率%.0f%%，连续打卡%d天",
                    totalWords, masteredWords, dueCount, stubbornCount, recentAccuracy, streak));

            if (dueCount > 30) {
                sb.append("，待复习较多，建议优先复习。");
            }
            if (stubbornCount > 5) {
                sb.append("，有较多顽固词需要重点攻克。");
            }

            // Fetch top 3 weak words
            List<UserWordBind> weakBinds = userWordBindRepository.findWeakByUserId(userId);
            if (weakBinds != null && !weakBinds.isEmpty()) {
                int limit = Math.min(3, weakBinds.size());
                List<Long> weakWordIds = weakBinds.stream().limit(limit).map(UserWordBind::getWordId).toList();
                List<Word> weakWords = wordRepository.findByIdIn(weakWordIds);
                if (!weakWords.isEmpty()) {
                    sb.append("\n【系统高优提示】用户当前最薄弱的几个单词（连续错误较多）是：");
                    sb.append(weakWords.stream().map(Word::getWord).reduce((a, b) -> a + ", " + b).orElse(""));
                    sb.append("。请在回复中自然地提及它们，并主动建议用户点击【开始深度学习】去攻克它们！");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to build learning stats for userId={}", user.getId(), e);
        }

        return sb.toString();
    }

    private List<Map<String, String>> buildStructuredHistory(Long sessionId) {
        List<ChatMessage> recent = chatMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(recent);
        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessage m : recent) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("role", m.getRole());
            entry.put("content", m.getContent());
            history.add(entry);
        }
        return history;
    }

    private String generateFallbackReply(String userMessage, User user) {
        String nickname = (user != null && user.getNickname() != null && !user.getNickname().isBlank())
                ? user.getNickname() : "同学";
        if (userMessage.contains("背") || userMessage.contains("单词") || userMessage.contains("学习")) {
            return String.format("%s，我们先从小步开始。每天稳稳练 15-30 分钟，比临时冲刺更有效。要不要我现在帮你开一组快速记忆？", nickname);
        }
        if (userMessage.contains("累") || userMessage.contains("难") || userMessage.contains("放弃")) {
            return String.format("%s，累了先缓一缓是可以的，但别把今天完全放掉。先休息一下，再回来做一个很小的任务就好。", nickname);
        }
        return String.format("我是 TiMo，你的备考教练，%s。你可以直接问我单词、计划、周报，或者让我帮你看看最近的学习状态。", nickname);
    }
}
