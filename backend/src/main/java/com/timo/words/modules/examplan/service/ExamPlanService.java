package com.timo.words.modules.examplan.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.examplan.entity.ExamPlan;
import com.timo.words.modules.examplan.repository.ExamPlanRepository;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamPlanService {

    private final ExamPlanRepository examPlanRepository;
    private final UserRepository userRepository;
    private final QuizRecordRepository quizRecordRepository;
    private final StringRedisTemplate stringRedisTemplate;

    // --- DTOs ---

    @Data
    public static class DialogRequest {
        private Long userId;
        private String answer;
    }

    @Data
    public static class DialogResponse {
        private String stage;
        private String message;
        private List<OptionItem> options;
        private Map<String, Object> collectedInfo;
        private boolean planReady;
        private PlanSummary planSummary;
    }

    @Data
    public static class OptionItem {
        private String value;
        private String label;
        public OptionItem(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    @Data
    public static class PlanSummary {
        private String examType;
        private int targetVocab;
        private int dailyNewWords;
        private int dailyReviewWords;
        private int estimatedDays;
        private int studyDaysPerWeek;
        private double dailyHours;
    }

    /**
     * 今日学习配额：用于 WordSelect / Dashboard 等页面在用户决定数量前
     * 校验"今日已学新词数 / 已复习数"是否已达 ExamPlan 上限。
     * 没有 active plan 时 target/remaining 为 null/-1，hasActivePlan=false。
     */
    @Data
    public static class DailyQuotaDTO {
        private Integer dailyNewWordsTarget;     // 计划值，null 表示无计划
        private Integer dailyReviewWordsTarget;
        private int todayNewWordsLearned;        // 今日已学新词（study_mode=quick_memory）
        private int todayReviewsCompleted;       // 今日已复习（unified_review + context_deep）
        private int newWordsRemaining;           // = max(0, target - learned)，无计划时返回 -1
        private int reviewsRemaining;
        private boolean hasActivePlan;
    }

    // --- Dialog State Machine ---

    private static final String STAGE_EXAM_TYPE = "exam_type";
    private static final String STAGE_CURRENT_LEVEL = "current_level";
    private static final String STAGE_TARGET_SCORE = "target_score";
    private static final String STAGE_STUDY_DAYS = "study_days";
    private static final String STAGE_DAILY_HOURS = "daily_hours";
    private static final String STAGE_WEAK_POINTS = "weak_points";
    private static final String STAGE_PLAN_READY = "plan_ready";

    private static final String DIALOG_KEY_PREFIX = "examplan:dialog:";
    private static final long DIALOG_TTL_MINUTES = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    private static class DialogState {
        private String stage = STAGE_EXAM_TYPE;
        private Map<String, Object> answers = new HashMap<>();
    }

    public DialogResponse startDialog(Long userId) {
        DialogState state = new DialogState();
        saveDialogState(userId, state);

        DialogResponse resp = new DialogResponse();
        resp.setStage(STAGE_EXAM_TYPE);
        resp.setMessage("你好！我是 TiMo，你的备考规划助手。首先，请告诉我你正在准备哪个考试？");
        resp.setOptions(List.of(
                new OptionItem("gaokao", "高考英语"),
                new OptionItem("cet4", "大学英语四级 (CET-4)"),
                new OptionItem("cet6", "大学英语六级 (CET-6)"),
                new OptionItem("gk", "考研英语")
        ));
        resp.setCollectedInfo(new HashMap<>());
        resp.setPlanReady(false);
        return resp;
    }

    public DialogResponse continueDialog(Long userId, String answer) {
        DialogState state = loadDialogState(userId);
        if (state == null) {
            state = new DialogState();
        }

        String currentStage = state.getStage();
        String nextStage;
        String nextMessage;
        List<OptionItem> options = null;

        switch (currentStage) {
            case STAGE_EXAM_TYPE:
                if (!isValidExamType(answer)) {
                    return errorResponse(currentStage, "请选择一个有效的考试类型");
                }
                state.getAnswers().put("examType", answer);
                nextStage = STAGE_CURRENT_LEVEL;
                nextMessage = getExamName(answer) + "，了解了！你目前的词汇量大概在什么水平？";
                options = List.of(
                        new OptionItem("beginner", "初学者 (< 1000词)"),
                        new OptionItem("basic", "基础 (1000-3000词)"),
                        new OptionItem("intermediate", "中级 (3000-5000词)"),
                        new OptionItem("advanced", "高级 (5000+词)")
                );
                break;

            case STAGE_CURRENT_LEVEL:
                if (!isValidLevel(answer)) {
                    return errorResponse(currentStage, "请选择一个有效的水平");
                }
                state.getAnswers().put("currentLevel", answer);
                nextStage = STAGE_TARGET_SCORE;
                nextMessage = "好的，你的目标分数是多少？（如果不确定，可以选择推荐值）";
                options = getTargetScoreOptions((String) state.getAnswers().get("examType"));
                break;

            case STAGE_TARGET_SCORE:
                state.getAnswers().put("targetScore", answer);
                nextStage = STAGE_STUDY_DAYS;
                nextMessage = "你计划用多少天来备考？";
                options = List.of(
                        new OptionItem("30", "1个月"),
                        new OptionItem("60", "2个月"),
                        new OptionItem("90", "3个月"),
                        new OptionItem("120", "4个月"),
                        new OptionItem("180", "6个月")
                );
                break;

            case STAGE_STUDY_DAYS:
                if (!isValidNumber(answer, 1, 365)) {
                    return errorResponse(currentStage, "请输入1-365之间的天数");
                }
                state.getAnswers().put("studyDays", (int) Double.parseDouble(answer));
                nextStage = STAGE_DAILY_HOURS;
                nextMessage = "每天你打算花多少时间背单词？";
                options = List.of(
                        new OptionItem("0.5", "30分钟以内"),
                        new OptionItem("1", "约1小时"),
                        new OptionItem("1.5", "约1.5小时"),
                        new OptionItem("2", "2小时以上")
                );
                break;

            case STAGE_DAILY_HOURS:
                if (!isValidNumber(answer, 0.5, 8)) {
                    return errorResponse(currentStage, "请输入0.5-8之间的小时数");
                }
                state.getAnswers().put("dailyHours", Double.parseDouble(answer));
                nextStage = STAGE_WEAK_POINTS;
                nextMessage = "你觉得自己哪些方面比较薄弱？（可多选，用逗号分隔）";
                options = List.of(
                        new OptionItem("vocabulary", "词汇量不足"),
                        new OptionItem("spelling", "拼写容易出错"),
                        new OptionItem("usage", "用法搭配不熟"),
                        new OptionItem("reading", "阅读理解弱"),
                        new OptionItem("none", "没有特别薄弱的")
                );
                break;

            case STAGE_WEAK_POINTS:
                state.getAnswers().put("weakPoints", answer);
                nextStage = STAGE_PLAN_READY;
                // Generate plan
                ExamPlan plan = generatePlan(userId, state.getAnswers());
                state.getAnswers().put("planId", plan.getId());
                nextMessage = "规划已生成！以下是你的个性化备考方案：";
                DialogResponse resp = buildPlanResponse(nextStage, nextMessage, plan, state.getAnswers());
                clearDialogState(userId);
                return resp;

            default:
                return errorResponse(currentStage, "对话已结束，请刷新页面重新开始");
        }

        state.setStage(nextStage);
        saveDialogState(userId, state);

        DialogResponse resp = new DialogResponse();
        resp.setStage(nextStage);
        resp.setMessage(nextMessage);
        resp.setOptions(options);
        resp.setCollectedInfo(new HashMap<>(state.getAnswers()));
        resp.setPlanReady(false);
        return resp;
    }

    @Transactional
    public ExamPlan generatePlan(Long userId, Map<String, Object> answers) {
        String examType = (String) answers.get("examType");
        String currentLevel = (String) answers.get("currentLevel");

        Object studyDaysObj = answers.get("studyDays");
        Object dailyHoursObj = answers.get("dailyHours");
        if (studyDaysObj == null || dailyHoursObj == null) {
            throw new BusinessException(400, "缺少必要的规划参数");
        }
        int studyDays = ((Number) studyDaysObj).intValue();
        double dailyHours = ((Number) dailyHoursObj).doubleValue();

        // Estimate current vocabulary from level
        int currentVocab = estimateVocab(currentLevel);
        int targetVocab = getTargetVocab(examType);

        // Calculate daily plan
        int wordsNeeded = Math.max(0, targetVocab - currentVocab);
        int dailyNewWords = Math.max(5, Math.min(50, wordsNeeded / Math.max(1, studyDays)));
        int dailyReviewWords = (int) (dailyNewWords * 2.5); // FSRS review estimation
        int estimatedDays = (int) Math.ceil((double) wordsNeeded / dailyNewWords);

        // Build plan JSON
        String planJson = buildPlanJson(examType, currentVocab, targetVocab,
                dailyNewWords, dailyReviewWords, estimatedDays, dailyHours, answers);

        // Deactivate old plans (JPA dirty checking auto-flushes on commit)
        examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .ifPresent(p -> p.setIsActive(false));

        ExamPlan plan = new ExamPlan();
        plan.setUserId(userId);
        plan.setExamType(examType);
        plan.setTargetVocab(targetVocab);
        plan.setDailyNewWords(dailyNewWords);
        plan.setDailyReviewWords(dailyReviewWords);
        plan.setEstimatedDays(estimatedDays);
        plan.setStudyDaysPerWeek(7);
        plan.setDailyHours(dailyHours);
        plan.setPlanJson(planJson);
        plan.setIsActive(true);
        plan = examPlanRepository.save(plan);

        // Update user profile
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        user.setExamType(examType);
        userRepository.save(user);

        return plan;
    }

    public DialogResponse getPlanStatus(Long userId) {
        ExamPlan plan = examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .orElse(null);

        DialogResponse resp = new DialogResponse();
        if (plan == null) {
            resp.setStage("no_plan");
            resp.setMessage("你还没有备考规划，点击开始规划吧！");
            resp.setPlanReady(false);
            return resp;
        }

        resp.setStage(STAGE_PLAN_READY);
        resp.setMessage("这是你当前的备考规划：");
        resp.setPlanReady(true);
        resp.setPlanSummary(buildSummary(plan));
        return resp;
    }

    /**
     * 计算用户今日学习配额：对比 ExamPlan 上限 vs 当日已完成的新词/复习数。
     *
     * 数据来源：quiz_records 表，按 study_mode 区分：
     *   - 新词：study_mode = "quick_memory"
     *   - 复习：study_mode ∈ {"unified_review", "context_deep"}
     *
     * Today 起点：本地时区当天 00:00:00。
     * 无 active plan 时 target=null、remaining=-1（前端据此显示"未设置规划"）。
     */
    public DailyQuotaDTO getTodayQuota(Long userId) {
        DailyQuotaDTO dto = new DailyQuotaDTO();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        int newLearned = (int) quizRecordRepository
                .countByUserIdAndCreatedAtAfterAndStudyMode(userId, todayStart, "quick_memory");
        int reviewsDone = (int) quizRecordRepository
                .countByUserIdAndCreatedAtAfterAndStudyModeIn(
                        userId, todayStart, List.of("unified_review", "context_deep"));
        dto.setTodayNewWordsLearned(newLearned);
        dto.setTodayReviewsCompleted(reviewsDone);

        ExamPlan plan = examPlanRepository
                .findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .orElse(null);

        if (plan == null) {
            dto.setHasActivePlan(false);
            dto.setDailyNewWordsTarget(null);
            dto.setDailyReviewWordsTarget(null);
            dto.setNewWordsRemaining(-1);
            dto.setReviewsRemaining(-1);
            return dto;
        }

        dto.setHasActivePlan(true);
        Integer newTarget = plan.getDailyNewWords();
        Integer reviewTarget = plan.getDailyReviewWords();
        dto.setDailyNewWordsTarget(newTarget);
        dto.setDailyReviewWordsTarget(reviewTarget);
        dto.setNewWordsRemaining(newTarget == null ? -1 : Math.max(0, newTarget - newLearned));
        dto.setReviewsRemaining(reviewTarget == null ? -1 : Math.max(0, reviewTarget - reviewsDone));
        return dto;
    }

    // --- Helpers ---

    private boolean isValidExamType(String type) {
        return Set.of("gaokao", "cet4", "cet6", "gk").contains(type);
    }

    private boolean isValidLevel(String level) {
        return Set.of("beginner", "basic", "intermediate", "advanced").contains(level);
    }

    private boolean isValidNumber(String s, double min, double max) {
        try {
            double v = Double.parseDouble(s);
            return v >= min && v <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getExamName(String type) {
        return switch (type) {
            case "gaokao" -> "高考英语";
            case "cet4" -> "四级";
            case "cet6" -> "六级";
            case "gk" -> "考研英语";
            default -> type;
        };
    }

    private int estimateVocab(String level) {
        return switch (level) {
            case "beginner" -> 800;
            case "basic" -> 2000;
            case "intermediate" -> 4000;
            case "advanced" -> 6000;
            default -> 2000;
        };
    }

    private int getTargetVocab(String examType) {
        return switch (examType) {
            case "gaokao" -> 3500;
            case "cet4" -> 4500;
            case "cet6" -> 6500;
            case "gk" -> 5500;
            default -> 5000;
        };
    }

    private List<OptionItem> getTargetScoreOptions(String examType) {
        return switch (examType) {
            case "gaokao" -> List.of(
                    new OptionItem("90", "及格 90分"),
                    new OptionItem("120", "良好 120分"),
                    new OptionItem("135", "优秀 135分"),
                    new OptionItem("150", "满分冲刺 150分"));
            case "cet4" -> List.of(
                    new OptionItem("425", "及格线 425分"),
                    new OptionItem("500", "良好 500分"),
                    new OptionItem("550", "优秀 550分"),
                    new OptionItem("600", "满分冲刺 600+"));
            case "cet6" -> List.of(
                    new OptionItem("425", "及格线 425分"),
                    new OptionItem("500", "良好 500分"),
                    new OptionItem("550", "优秀 550分"),
                    new OptionItem("600", "满分冲刺 600+"));
            default -> List.of(
                    new OptionItem("pass", "通过即可"),
                    new OptionItem("good", "良好成绩"),
                    new OptionItem("excellent", "追求高分"));
        };
    }

    private String buildPlanJson(String examType, int currentVocab, int targetVocab,
                                  int dailyNew, int dailyReview, int estimatedDays,
                                  double dailyHours, Map<String, Object> answers) {
        try {
            Map<String, Object> planData = new LinkedHashMap<>();
            planData.put("examType", examType);
            planData.put("currentVocab", currentVocab);
            planData.put("targetVocab", targetVocab);
            planData.put("dailyNewWords", dailyNew);
            planData.put("dailyReviewWords", dailyReview);
            planData.put("estimatedDays", estimatedDays);
            planData.put("dailyHours", dailyHours);
            planData.put("currentLevel", answers.get("currentLevel"));
            planData.put("targetScore", answers.get("targetScore"));
            planData.put("weakPoints", answers.get("weakPoints"));
            return objectMapper.writeValueAsString(planData);
        } catch (Exception e) {
            throw new BusinessException(500, "计划生成失败");
        }
    }

    private DialogResponse buildPlanResponse(String stage, String message,
                                              ExamPlan plan, Map<String, Object> answers) {
        DialogResponse resp = new DialogResponse();
        resp.setStage(stage);
        resp.setMessage(message);
        resp.setPlanReady(true);
        resp.setPlanSummary(buildSummary(plan));
        resp.setCollectedInfo(new HashMap<>(answers));
        return resp;
    }

    private PlanSummary buildSummary(ExamPlan plan) {
        PlanSummary summary = new PlanSummary();
        summary.setExamType(plan.getExamType());
        summary.setTargetVocab(plan.getTargetVocab());
        summary.setDailyNewWords(plan.getDailyNewWords());
        summary.setDailyReviewWords(plan.getDailyReviewWords());
        summary.setEstimatedDays(plan.getEstimatedDays());
        summary.setStudyDaysPerWeek(plan.getStudyDaysPerWeek());
        summary.setDailyHours(plan.getDailyHours());
        return summary;
    }

    private DialogResponse errorResponse(String stage, String message) {
        DialogResponse resp = new DialogResponse();
        resp.setStage(stage);
        resp.setMessage(message);
        resp.setPlanReady(false);
        return resp;
    }

    // --- Redis Dialog State ---

    private String getDialogKey(Long userId) {
        return DIALOG_KEY_PREFIX + userId;
    }

    private void saveDialogState(Long userId, DialogState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(getDialogKey(userId), json, DIALOG_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to save dialog state for userId={}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("保存对话状态失败，请稍后重试", e);
        }
    }

    private DialogState loadDialogState(Long userId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(getDialogKey(userId));
            if (json != null) {
                return objectMapper.readValue(json, DialogState.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load dialog state for userId={}: {}", userId, e.getMessage());
        }
        return null;
    }

    private void clearDialogState(Long userId) {
        stringRedisTemplate.delete(getDialogKey(userId));
    }
}
