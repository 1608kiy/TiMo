package com.timo.words.modules.statistics.service;

import com.timo.words.modules.calendar.entity.CheckinRecord;
import com.timo.words.modules.calendar.repository.CheckinRecordRepository;
import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.word.repository.WordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private QuizRecordRepository quizRecordRepository;
    @Mock private UserWordBindRepository userWordBindRepository;
    @Mock private CheckinRecordRepository checkinRecordRepository;
    @Mock private WordRepository wordRepository;
    @InjectMocks private StatisticsService statisticsService;

    @Test
    void getOverview_empty() {
        when(userWordBindRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(quizRecordRepository.countByUserId(1L)).thenReturn(0L);
        when(quizRecordRepository.avgGradeByUserId(1L)).thenReturn(null);
        when(checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(1L)).thenReturn(Collections.emptyList());

        StatisticsService.OverviewDTO overview = statisticsService.getOverview(1L);

        assertEquals(0, overview.getTotalWordsStudied());
        assertEquals(0, overview.getMasteredWords());
        assertEquals(0, overview.getTotalRecords());
        assertEquals(0, overview.getAvgAccuracy());
        assertEquals(0, overview.getStudyDays());
        assertEquals(0, overview.getCurrentStreak());
    }

    @Test
    void getOverview_withData() {
        // Mastered now driven by masteredAt timestamp (set by StudyService.updateMasteredStatus)
        // instead of the legacy stability >= 1.2 check.
        UserWordBind mastered = new UserWordBind();
        mastered.setStability(40.0);
        mastered.setMasteredAt(LocalDateTime.now().minusDays(1));
        UserWordBind learning = new UserWordBind();
        learning.setStability(0.8);

        when(userWordBindRepository.findByUserId(1L)).thenReturn(List.of(mastered, learning));
        when(quizRecordRepository.countByUserId(1L)).thenReturn(100L);
        when(quizRecordRepository.avgGradeByUserId(1L)).thenReturn(3.0);
        when(checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(1L))
                .thenReturn(Collections.emptyList());

        StatisticsService.OverviewDTO overview = statisticsService.getOverview(1L);

        assertEquals(2, overview.getTotalWordsStudied());
        assertEquals(1, overview.getMasteredWords());
        assertEquals(100, overview.getTotalRecords());
        // avg accuracy = (3.0 - 1.0) / 3.0 * 100 = 66.7
        assertEquals(66.7, overview.getAvgAccuracy(), 0.1);
    }

    @Test
    void getRetention_empty() {
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        StatisticsService.RetentionDTO retention = statisticsService.getRetention(1L, 7);

        assertEquals(7, retention.getDates().size());
        assertTrue(retention.getRetentionRates().stream().allMatch(r -> r == 0.0));
    }

    @Test
    void getRetention_withRecords() {
        QuizRecord correct = new QuizRecord();
        correct.setGrade(4.0);
        correct.setCreatedAt(LocalDateTime.now());
        QuizRecord wrong = new QuizRecord();
        wrong.setGrade(1.0);
        wrong.setCreatedAt(LocalDateTime.now());

        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(List.of(correct, correct, wrong));

        StatisticsService.RetentionDTO retention = statisticsService.getRetention(1L, 1);

        assertEquals(1, retention.getDates().size());
        // 2/3 = 66.7%
        assertEquals(66.7, retention.getRetentionRates().get(0), 0.1);
    }

    @Test
    void getHeatmap_empty() {
        when(checkinRecordRepository.findByUserIdOrderByCheckinDateDesc(1L))
                .thenReturn(Collections.emptyList());

        StatisticsService.HeatmapDTO heatmap = statisticsService.getHeatmap(1L);

        assertTrue(heatmap.getData().isEmpty());
        assertEquals(1, heatmap.getMaxCount());
    }

    @Test
    void getDailyStats_empty() {
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        StatisticsService.DailyStatsDTO stats = statisticsService.getDailyStats(1L, 7);

        assertEquals(7, stats.getDates().size());
        assertTrue(stats.getQuickMemory().stream().allMatch(n -> n == 0));
    }

    @Test
    void getWeakWords_empty() {
        when(userWordBindRepository.findWeakByUserId(1L)).thenReturn(Collections.emptyList());

        List<StatisticsService.WeakWordDTO> weak = statisticsService.getWeakWords(1L);
        assertTrue(weak.isEmpty());
    }

    @Test
    void getWeakWords_returnsLimited() {
        List<UserWordBind> weakBinds = new java.util.ArrayList<>();
        for (int i = 0; i < 35; i++) {
            UserWordBind b = new UserWordBind();
            b.setWordId((long) i);
            b.setConsecutiveErrors(i % 5);
            b.setDifficulty(5.0 + i % 3);
            weakBinds.add(b);
        }
        when(userWordBindRepository.findWeakByUserId(1L)).thenReturn(weakBinds);

        List<StatisticsService.WeakWordDTO> result = statisticsService.getWeakWords(1L);
        assertEquals(30, result.size(), "Should limit to 30");
    }

    @Test
    void getForgettingCurve_empty() {
        when(userWordBindRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        StatisticsService.ForgettingCurveDTO curve = statisticsService.getForgettingCurve(1L, 7);

        assertEquals(8, curve.getDays().size()); // D+0 to D+7
        assertEquals(8, curve.getExpectedR().size());
        assertEquals(8, curve.getActualR().size());
    }

    @Test
    void getReactionTime_empty() {
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        StatisticsService.ReactionTimeDTO rt = statisticsService.getReactionTime(1L, 30);

        assertTrue(rt.getQuickMemory().isEmpty());
        assertTrue(rt.getContextDeep().isEmpty());
        assertTrue(rt.getUnifiedReview().isEmpty());
    }
}
