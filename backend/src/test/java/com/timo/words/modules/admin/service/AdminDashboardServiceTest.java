package com.timo.words.modules.admin.service;

import com.timo.words.modules.admin.repository.AiCallLogRepository;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.repository.WordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WordRepository wordRepository;
    @Mock private AiCallLogRepository aiCallLogRepository;
    @Mock private QuizRecordRepository quizRecordRepository;

    @InjectMocks private AdminDashboardService adminDashboardService;

    @Test
    void getOverview_returnsAllMetrics() {
        when(userRepository.count()).thenReturn(100L);
        when(wordRepository.count()).thenReturn(18000L);
        when(aiCallLogRepository.countSince(any())).thenReturn(50L, 200L);
        when(aiCallLogRepository.sumTokensSince(any())).thenReturn(10000L, 50000L);
        when(aiCallLogRepository.countByStatusSince(any(), any())).thenReturn(45L);
        when(wordRepository.countByExamTypeGroup()).thenReturn(List.of());
        when(quizRecordRepository.countByStudyModeGlobal()).thenReturn(List.of());

        Map<String, Object> result = adminDashboardService.getOverview();

        assertEquals(100L, result.get("totalUsers"));
        assertEquals(18000L, result.get("totalWords"));
        assertEquals(50L, result.get("todayAiCalls"));
        assertEquals(200L, result.get("weekAiCalls"));
        assertEquals(10000L, result.get("todayTokens"));
        assertEquals(50000L, result.get("weekTokens"));
        assertNotNull(result.get("aiSuccessRate"));
    }

    @Test
    void getTrend_returnsDailyStats() {
        when(aiCallLogRepository.dailyStatsSince(any())).thenReturn(List.of());

        Map<String, Object> result = adminDashboardService.getTrend(7);

        assertNotNull(result.get("aiDailyStats"));
    }
}
