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
    private final UserWordBindRepository userWordBindRepository;
    private final QuizRecordRepository quizRecordRepository;
    private final CheckinRecordRepository checkinRecordRepository;
    private final ConversationQuizLogRepository conversationQuizLogRepository;
    private final ExamPlanRepository examPlanRepository;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是 TiMo，一个友善、专业的备考教练和单词学习助手。
            你的职责：
            1. 回答用户关于单词学习的问题
            2. 根据用户的学习数据给出个性化建议
            3. 鼓励用户坚持学习，适时提醒
            4. 解释单词的用法、搭配、记忆技巧

            回复要求：
            - 简洁友好，像朋友聊天一样
            - 每次回复控制在100字以内
            - 如果用户问的是具体单词，给出释义、例句和记忆技巧
            - 如果用户情绪低落，给予鼓励

            回复格式要求：
            - 在回复末尾用JSON格式附带元数据（单独一行，不放在代码块中）：
            {"state":"<idle|success|alert>","actions":["<可选操作标签>"]}
            - state字段：idle=正常回复，success=用户有进步或完成任务，alert=需要关注学习进度
            - actions字段：可选的操作建议标签，如"开始快速记忆"、"开始深度学习"、"查看周报"、"开始今日学习"
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
    public static class WeeklyReportResponse {
        private int totalStudied;
        private int newWordsLearned;
        private int reviewsCompleted;
        private double avgAccuracy;
        private int studyDays;
        private int longestStreak;
        private String summary;
        private List<String> suggestions;
    }

    @Data
    public static class PassageRequest {
        @jakarta.validation.constraints.NotEmpty(message = "单词列表不能为空")
        private List<String> words;
    }

    @Data
    public static class PassageResponse {
        private String passage;
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
        private String etymology;
        private String mnemonic;
        private String usage;
        private String synonyms;
        private String example;
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
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted() + "\n\n用户信息：" + userInfo;

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
            rawReply = generateFallbackReply(req.getMessage());
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

        // Save assistant message with card data
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        if (!actions.isEmpty()) {
            try {
                assistantMsg.setSuggestedActions(objectMapper.writeValueAsString(actions));
            } catch (Exception ignored) {}
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

        // Recommend daily new words based on available vocab and accuracy
        int dailyNew = Math.max(10, Math.min(30, 50 - masteredCount / 10));
        int dailyReview = Math.max(20, dueCount);

        // Suggest mode based on weak words
        String mode = errorProneCount > 5 ? "context_deep" : "quick_memory";
        String reason = errorProneCount > 5
                ? "你有一些反复出错的单词，建议用语境深度学习加强记忆"
                : "当前学习状态良好，可以用快速记忆巩固";

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
        } catch (Exception ignored) {}

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

    // --- Analyze Word ---

    public AnalyzeWordResponse analyzeWord(String word) {
        String prompt = """
                请对英语单词 "%s" 进行深度分析。

                要求：
                1. etymology: 词根词缀拆解分析，帮助理解单词构成
                2. mnemonic: 助记技巧，可以是谐音、联想、拆分等
                3. usage: 常见搭配和用法说明
                4. synonyms: 近义词辨析，说明它们之间的区别
                5. example: 一个地道的例句（英文），并附中文翻译

                严格按照以下JSON格式返回，不要添加任何其他内容：

                {
                  "etymology": "词根词缀分析",
                  "mnemonic": "助记技巧",
                  "usage": "常见搭配和用法",
                  "synonyms": "近义词辨析",
                  "example": "英文例句 — 中文翻译"
                }
                """.formatted(word);

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
                resp.setEtymology(node.path("etymology").asText(""));
                resp.setMnemonic(node.path("mnemonic").asText(""));
                resp.setUsage(node.path("usage").asText(""));
                resp.setSynonyms(node.path("synonyms").asText(""));
                resp.setExample(node.path("example").asText(""));
                return resp;
            } catch (Exception e) {
                log.warn("Failed to parse analyzeWord JSON for word={}", word, e);
            }
        }

        log.warn("Using fallback analysis for word={} (DeepSeek unavailable)", word);
        return buildFallbackAnalysis(word);
    }

    private AnalyzeWordResponse buildFallbackAnalysis(String word) {
        AnalyzeWordResponse resp = new AnalyzeWordResponse();
        resp.setEtymology("暂无词根词缀分析，请查阅词典了解该词的构成。");
        resp.setMnemonic("暂无助记技巧，建议结合例句理解记忆。");
        resp.setUsage("常见搭配请参考词典释义。");
        resp.setSynonyms("暂无近义词辨析。");
        resp.setExample("\"" + word + "\" is commonly used in academic contexts. — \"" + word + "\" 常用于学术语境中。");
        return resp;
    }

    // --- Weekly Report ---

    public WeeklyReportResponse getWeeklyReport(Long userId) {
        LocalDateTime weekAgo = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<com.timo.words.modules.study.entity.QuizRecord> weekRecords =
                quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, weekAgo, now);

        int totalStudied = weekRecords.size();
        long newWords = quizRecordRepository.countDistinctWordIdByUserIdSince(userId, weekAgo);
        int reviewsCompleted = totalStudied;

        double avgAccuracy = 0;
        if (totalStudied > 0) {
            long correct = weekRecords.stream()
                    .filter(r -> r.getGrade() != null && r.getGrade() >= 3.0).count();
            avgAccuracy = Math.round(correct * 1000.0 / totalStudied) / 10.0;
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

        WeeklyReportResponse resp = new WeeklyReportResponse();
        resp.setTotalStudied(totalStudied);
        resp.setNewWordsLearned((int) newWords);
        resp.setReviewsCompleted(reviewsCompleted);
        resp.setAvgAccuracy(avgAccuracy);
        resp.setStudyDays((int) studyDays);
        resp.setLongestStreak(longestStreak);
        resp.setSummary(String.format("本周学习%d天，共学%d个单词，正确率%.0f%%",
                studyDays, newWords, avgAccuracy));
        resp.setSuggestions(suggestions);
        return resp;
    }

    private int calculateStreak(List<com.timo.words.modules.calendar.entity.CheckinRecord> checkins) {
        if (checkins.isEmpty()) return 0;
        int streak = 1;
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate expected = today;
        for (var c : checkins) {
            if (c.getCheckinDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (c.getCheckinDate().isBefore(expected)) {
                break;
            }
        }
        return Math.max(0, streak - 1);
    }

    // --- Generate Passage ---

    public PassageResponse generatePassage(PassageRequest req) {
        List<String> words = req.getWords();
        String wordList = String.join(", ", words);

        String prompt = """
                请写一段100-150词的英文短文，自然地嵌入以下单词：%s。

                要求：
                1. 短文内容连贯、语法正确、难度适中（大学英语水平）
                2. 每个目标单词在短文中出现一次，保留原文单词不要替换
                3. 严格按照以下JSON格式返回，不要添加任何其他内容：

                {
                  "passage": "完整的英文短文，保留所有目标单词原文",
                  "wordSentences": {
                    "单词1": "包含该单词的完整例句",
                    "单词2": "包含该单词的完整例句"
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
                PassageResponse resp = new PassageResponse();
                resp.setPassage(node.path("passage").asText(""));

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

    private PassageResponse buildFallbackPassage(List<String> words) {
        StringBuilder passage = new StringBuilder();
        Map<String, String> wordSentences = new HashMap<>();

        String[] templates = {
            "She had to %s her old habits to make room for growth.",
            "His %s to learn new languages impressed everyone around him.",
            "The student was %s from class due to a sudden illness.",
            "We should %s every opportunity that comes our way.",
            "The %s of the plan was questioned by the committee.",
            "They decided to %s the project after careful consideration.",
            "Her %s in solving problems made her a valuable team member.",
            "The teacher noticed the student was %s during the lesson.",
            "He showed great %s in handling difficult situations.",
            "Being %s from the meeting meant missing important decisions."
        };

        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String template = templates[i % templates.length];
            String sentence = String.format(template, word);
            wordSentences.put(word, sentence);
            if (i > 0) passage.append(" ");
            passage.append(sentence);
        }

        PassageResponse resp = new PassageResponse();
        resp.setPassage(passage.toString());
        resp.setWordSentences(wordSentences);
        return resp;
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
        // Fallback: simple definition questions
        Map<String, Object> fallback = new LinkedHashMap<>();
        List<Map<String, Object>> fallbackQuestions = new ArrayList<>();
        for (String word : words) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("word", word);
            q.put("question", "请选择 " + word + " 的正确释义");
            q.put("options", List.of("释义A", "释义B", "释义C", "释义D"));
            q.put("correctIndex", 0);
            q.put("explanation", "请查阅词典获取准确释义");
            fallbackQuestions.add(q);
        }
        fallback.put("questions", fallbackQuestions);
        return fallback;
    }

    // --- Helpers ---

    private String buildUserInfo(User user) {
        if (user == null) return "新用户";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("用户名：%s，考试类型：%s",
                user.getNickname() != null ? user.getNickname() : user.getEmail(),
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
                sb.append("，待复习较多，建议优先复习");
            }
            if (stubbornCount > 5) {
                sb.append("，有较多顽固词需要重点攻克");
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

    private String generateFallbackReply(String userMessage) {
        if (userMessage.contains("背") || userMessage.contains("单词") || userMessage.contains("学习")) {
            return "学习需要坚持！每天花15-30分钟背单词，比突击效果好得多。要不要现在开始？";
        }
        if (userMessage.contains("累") || userMessage.contains("难") || userMessage.contains("放弃")) {
            return "学习的路上难免有瓶颈，但坚持就是胜利！适当休息一下，然后继续加油！";
        }
        return "我是TiMo，你的备考教练。有什么关于单词学习的问题可以问我哦！";
    }
}
