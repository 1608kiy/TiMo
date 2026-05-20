package com.timo.words.modules.admin.service;

import com.timo.words.modules.admin.repository.AiCallLogRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final WordRepository wordRepository;
    private final AiCallLogRepository aiCallLogRepository;
    private final QuizRecordRepository quizRecordRepository;

    public Map<String, Object> getOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekAgo = todayStart.minusDays(7);

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", userRepository.count());
        result.put("totalWords", wordRepository.count());
        result.put("todayAiCalls", aiCallLogRepository.countSince(todayStart));
        result.put("weekAiCalls", aiCallLogRepository.countSince(weekAgo));
        result.put("todayTokens", aiCallLogRepository.sumTokensSince(todayStart));
        result.put("weekTokens", aiCallLogRepository.sumTokensSince(weekAgo));
        result.put("aiSuccessRate", calculateSuccessRate(todayStart));
        result.put("examTypeDistribution", wordRepository.countByExamTypeGroup());
        result.put("modeDistribution", quizRecordRepository.countByStudyModeGlobal());
        return result;
    }

    public Map<String, Object> getTrend(int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        Map<String, Object> result = new HashMap<>();
        result.put("aiDailyStats", aiCallLogRepository.dailyStatsSince(since));
        return result;
    }

    private double calculateSuccessRate(LocalDateTime since) {
        long total = aiCallLogRepository.countSince(since);
        if (total == 0) return 1.0;
        long success = aiCallLogRepository.countByStatusSince("SUCCESS", since);
        return (double) success / total;
    }
}
