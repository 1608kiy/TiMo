package com.timo.words.modules.examplan.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.examplan.entity.ExamPlan;
import com.timo.words.modules.examplan.repository.ExamPlanRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamPlanServiceTest {

    @Mock private ExamPlanRepository examPlanRepository;
    @Mock private UserRepository userRepository;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @InjectMocks private ExamPlanService examPlanService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setExamType("cet4");
    }

    // --- startDialog ---

    @Test
    void startDialog_returnsExamTypeStage() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        ExamPlanService.DialogResponse resp = examPlanService.startDialog(1L);

        assertEquals("exam_type", resp.getStage());
        assertNotNull(resp.getMessage());
        assertNotNull(resp.getOptions());
        assertEquals(7, resp.getOptions().size());
        assertFalse(resp.isPlanReady());
    }

    // --- continueDialog: exam_type stage ---

    @Test
    void continueDialog_validExamType() {
        // Setup: mock Redis load/save
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // no saved state

        ExamPlanService.DialogResponse resp = examPlanService.continueDialog(1L, "cet4");

        assertEquals("current_level", resp.getStage());
        assertTrue(resp.getMessage().contains("四级"));
        assertNotNull(resp.getOptions());
    }

    @Test
    void continueDialog_invalidExamType() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        ExamPlanService.DialogResponse resp = examPlanService.continueDialog(1L, "invalid");

        assertEquals("exam_type", resp.getStage()); // stays on same stage
        assertTrue(resp.getMessage().contains("有效"));
    }

    // --- continueDialog: study_days validation ---

    @Test
    void continueDialog_invalidStudyDays() {
        // Simulate being at study_days stage via Redis state
        String stateJson = "{\"stage\":\"study_days\",\"answers\":{\"examType\":\"cet4\",\"currentLevel\":\"basic\",\"targetScore\":\"425\"}}";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(stateJson);

        ExamPlanService.DialogResponse resp = examPlanService.continueDialog(1L, "500");

        assertEquals("study_days", resp.getStage());
        assertTrue(resp.getMessage().contains("1-365"));
    }

    // --- getPlanStatus ---

    @Test
    void getPlanStatus_noPlan() {
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        ExamPlanService.DialogResponse resp = examPlanService.getPlanStatus(1L);

        assertEquals("no_plan", resp.getStage());
        assertFalse(resp.isPlanReady());
    }

    @Test
    void getPlanStatus_hasPlan() {
        ExamPlan plan = new ExamPlan();
        plan.setExamType("cet4");
        plan.setTargetVocab(5000);
        plan.setDailyNewWords(20);
        plan.setDailyReviewWords(50);
        plan.setEstimatedDays(60);
        plan.setStudyDaysPerWeek(7);
        plan.setDailyHours(1.0);

        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(plan));

        ExamPlanService.DialogResponse resp = examPlanService.getPlanStatus(1L);

        assertEquals("plan_ready", resp.getStage());
        assertTrue(resp.isPlanReady());
        assertNotNull(resp.getPlanSummary());
        assertEquals("cet4", resp.getPlanSummary().getExamType());
        assertEquals(5000, resp.getPlanSummary().getTargetVocab());
    }

    // --- generatePlan ---

    @Test
    void generatePlan_basicFlow() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(examPlanRepository.save(any())).thenAnswer(inv -> {
            ExamPlan p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        Map<String, Object> answers = new HashMap<>();
        answers.put("examType", "cet4");
        answers.put("currentLevel", "basic");
        answers.put("studyDays", 60);
        answers.put("dailyHours", 1.0);

        ExamPlan plan = examPlanService.generatePlan(1L, answers);

        assertNotNull(plan);
        assertEquals("cet4", plan.getExamType());
        assertEquals(4500, plan.getTargetVocab()); // CET-4 target
        assertTrue(plan.getDailyNewWords() >= 5);
        assertTrue(plan.getEstimatedDays() > 0);
        assertEquals(true, plan.getIsActive());
    }

    @Test
    void generatePlan_deactivatesOldPlan() {
        ExamPlan oldPlan = new ExamPlan();
        oldPlan.setId(10L);
        oldPlan.setIsActive(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(oldPlan));
        when(examPlanRepository.save(any())).thenAnswer(inv -> {
            ExamPlan p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        Map<String, Object> answers = new HashMap<>();
        answers.put("examType", "cet6");
        answers.put("currentLevel", "intermediate");
        answers.put("studyDays", 90);
        answers.put("dailyHours", 1.5);

        ExamPlan plan = examPlanService.generatePlan(1L, answers);

        assertFalse(oldPlan.getIsActive());
        assertEquals("cet6", plan.getExamType());
        assertEquals(6500, plan.getTargetVocab()); // CET-6 target
    }

    @Test
    void generatePlan_missingParams_throws() {
        Map<String, Object> answers = new HashMap<>();
        answers.put("examType", "cet4");
        // missing studyDays and dailyHours

        assertThrows(BusinessException.class, () -> examPlanService.generatePlan(1L, answers));
    }

    // --- target vocab mapping ---

    @Test
    void generatePlan_allExamTypes() {
        String[][] cases = {
                {"cet4", "4500"}, {"cet6", "6500"}, {"gk", "5500"},
                {"ielts", "7000"}, {"toefl", "8000"}, {"tem4", "6000"}, {"tem8", "10000"}
        };

        for (String[] tc : cases) {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(examPlanRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.empty());
            when(examPlanRepository.save(any(ExamPlan.class))).thenAnswer(inv -> {
                ExamPlan p = inv.getArgument(0);
                if (p == null) {
                    p = new ExamPlan();
                }
                p.setId(1L);
                return p;
            });

            Map<String, Object> answers = new HashMap<>();
            answers.put("examType", tc[0]);
            answers.put("currentLevel", "beginner");
            answers.put("studyDays", 90);
            answers.put("dailyHours", 1.0);

            ExamPlan plan = examPlanService.generatePlan(1L, answers);
            assertEquals(Integer.parseInt(tc[1]), plan.getTargetVocab(),
                    "Target vocab for " + tc[0]);
        }
    }
}
