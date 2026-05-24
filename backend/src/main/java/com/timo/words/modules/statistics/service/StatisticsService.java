package com.timo.words.modules.statistics.service;

import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final QuizRecordRepository quizRecordRepository;
    private final UserWordBindRepository userWordBindRepository;
    private final WordRepository wordRepository;

    // --- DTOs ---

    @Data
    public static class OverviewDTO {
        private int totalWordsStudied;
        private int masteredWords;
        private int totalRecords;
        private double avgAccuracy;
        private int studyDays;
        private int currentStreak;
    }

    @Data
    public static class RetentionDTO {
        private List<String> dates;
        private List<Double> retentionRates;
    }

    @Data
    public static class HeatmapDTO {
        private List<Object[]> data; // [date, count]
        private int maxCount;
    }

    @Data
    public static class DailyStatsDTO {
        private List<String> dates;
        private List<Integer> quickMemory;
        private List<Integer> contextDeep;
        private List<Integer> unifiedReview;
    }

    @Data
    public static class ReactionTimeDTO {
        private List<Integer> quickMemory;
        private List<Integer> contextDeep;
        private List<Integer> unifiedReview;
    }

    @Data
    public static class WeakWordDTO {
        private Long wordId;
        private String word;
        private int consecutiveErrors;
        private double difficulty;
    }

    @Data
    public static class ForgettingCurveDTO {
        private List<Double> expectedR;   // FSRS predicted R values over days
        private List<Double> actualR;     // Actual retention from quiz_records
        private List<String> days;        // Day labels
    }

    // --- Implementations ---

    public OverviewDTO getOverview(Long userId) {
        OverviewDTO dto = new OverviewDTO();

        List<UserWordBind> binds = userWordBindRepository.findByUserId(userId);
        dto.setTotalWordsStudied(binds.size());
        // Mastered = words promoted by StudyService.updateMasteredStatus (S > 21d, recent grade ≥ 3.5,
        // no error streak). Replaces the legacy "stability >= 1.2" check which was tied to the old
        // [0.5, 1.5] stability bound and lost all meaning after the FSRS bounds fix.
        dto.setMasteredWords((int) binds.stream()
                .filter(b -> b.getMasteredAt() != null)
                .count());

        dto.setTotalRecords((int) quizRecordRepository.countByUserId(userId));

        Double avg = quizRecordRepository.avgGradeByUserId(userId);
        // Grade 1.0~4.0 → percentage: (avg-1)/3 * 100
        dto.setAvgAccuracy(avg != null ? Math.round((avg - 1.0) / 3.0 * 1000) / 10.0 : 0);

        // Study days + current streak derived from distinct quiz-record dates
        List<LocalDate> studyDates = quizRecordRepository.findDistinctStudyDatesByUserId(userId)
                .stream().map(java.sql.Date::toLocalDate).toList();
        dto.setStudyDays(studyDates.size());
        dto.setCurrentStreak(calculateStreak(studyDates));

        return dto;
    }

    public RetentionDTO getRetention(Long userId, int days) {
        RetentionDTO dto = new RetentionDTO();
        List<String> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(days - 1).atStartOfDay();

        // Single query: fetch all records in the range, group by date in Java
        List<QuizRecord> allRecords = quizRecordRepository.findByUserIdAndCreatedAtBetween(
                userId, since, today.atTime(LocalTime.MAX));
        Map<String, List<QuizRecord>> byDate = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate().toString()));

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<QuizRecord> dayRecords = byDate.getOrDefault(date.toString(), List.of());
            double rate = 0;
            if (!dayRecords.isEmpty()) {
                long correct = dayRecords.stream().filter(r -> r.getGrade() != null && r.getGrade() >= 3.0).count();
                rate = Math.round(correct * 1000.0 / dayRecords.size()) / 10.0;
            }
            dates.add(date.toString());
            rates.add(rate);
        }

        dto.setDates(dates);
        dto.setRetentionRates(rates);
        return dto;
    }

    public ForgettingCurveDTO getForgettingCurve(Long userId, int numDays) {
        ForgettingCurveDTO dto = new ForgettingCurveDTO();
        List<String> days = new ArrayList<>();
        List<Double> expectedR = new ArrayList<>();
        List<Double> actualR = new ArrayList<>();

        List<UserWordBind> binds = userWordBindRepository.findByUserId(userId);

        // Single query: fetch all records in the range, group by date in Java
        LocalDateTime sinceDate = LocalDate.now().minusDays(numDays).atStartOfDay();
        List<QuizRecord> allRecords = quizRecordRepository.findByUserIdAndCreatedAtBetween(
                userId, sinceDate, LocalDate.now().atTime(LocalTime.MAX));
        Map<String, Double> avgGradeByDate = allRecords.stream()
                .filter(r -> r.getGrade() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCreatedAt().toLocalDate().toString(),
                        Collectors.averagingDouble(QuizRecord::getGrade)));

        for (int d = 0; d <= numDays; d++) {
            final int day = d;
            days.add("D+" + d);

            // Expected: average FSRS R for all binds at day d
            double avgExpected = binds.stream()
                    .filter(b -> b.getStability() != null && b.getStability() > 0)
                    .mapToDouble(b -> Math.exp(Math.log(0.9) * day / b.getStability()))
                    .average().orElse(0);
            expectedR.add(Math.round(avgExpected * 100.0) / 100.0);

            // Actual: from pre-computed map
            String dateKey = LocalDate.now().minusDays(day).toString();
            Double avgGrade = avgGradeByDate.get(dateKey);
            double actual = avgGrade != null ? (avgGrade - 1.0) / 3.0 : 0;
            actualR.add(Math.round(actual * 100.0) / 100.0);
        }

        dto.setDays(days);
        dto.setExpectedR(expectedR);
        dto.setActualR(actualR);
        return dto;
    }

    public HeatmapDTO getHeatmap(Long userId) {
        // Derive heatmap from quiz records: count per day = number of records that day.
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(365).atStartOfDay();
        List<QuizRecord> records = quizRecordRepository.findByUserIdAndCreatedAtBetween(
                userId, since, today.atTime(LocalTime.MAX));
        Map<LocalDate, Long> countsByDay = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate(), Collectors.counting()));

        List<Object[]> data = countsByDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Long>comparingByKey().reversed())
                .map(e -> new Object[]{e.getKey().toString(), e.getValue().intValue()})
                .collect(Collectors.toList());
        int maxCount = countsByDay.values().stream().mapToInt(Long::intValue).max().orElse(1);

        HeatmapDTO dto = new HeatmapDTO();
        dto.setData(data);
        dto.setMaxCount(Math.max(maxCount, 1));
        return dto;
    }

    public DailyStatsDTO getDailyStats(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(days - 1).atStartOfDay();

        List<String> dates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            dates.add(today.minusDays(i).toString());
        }

        // Single query for all records in the 30-day window
        List<QuizRecord> allRecords = quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, since, today.atTime(LocalTime.MAX));
        Map<String, List<QuizRecord>> byDate = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate().toString()));

        List<Integer> qm = new ArrayList<>();
        List<Integer> cd = new ArrayList<>();
        List<Integer> ur = new ArrayList<>();

        for (String date : dates) {
            List<QuizRecord> dayRecords = byDate.getOrDefault(date, List.of());
            qm.add((int) dayRecords.stream().filter(r -> "quick_memory".equals(r.getStudyMode())).count());
            cd.add((int) dayRecords.stream().filter(r -> "context_deep".equals(r.getStudyMode())).count());
            ur.add((int) dayRecords.stream().filter(r -> "unified_review".equals(r.getStudyMode())).count());
        }

        DailyStatsDTO dto = new DailyStatsDTO();
        dto.setDates(dates);
        dto.setQuickMemory(qm);
        dto.setContextDeep(cd);
        dto.setUnifiedReview(ur);
        return dto;
    }

    public ReactionTimeDTO getReactionTime(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(days - 1).atStartOfDay();
        List<QuizRecord> records = quizRecordRepository.findByUserIdAndCreatedAtBetween(userId, since, today.atTime(LocalTime.MAX));

        ReactionTimeDTO dto = new ReactionTimeDTO();
        dto.setQuickMemory(records.stream()
                .filter(r -> "quick_memory".equals(r.getStudyMode()) && r.getReactionTimeMs() != null)
                .map(QuizRecord::getReactionTimeMs)
                .collect(Collectors.toList()));
        dto.setContextDeep(records.stream()
                .filter(r -> "context_deep".equals(r.getStudyMode()) && r.getReactionTimeMs() != null)
                .map(QuizRecord::getReactionTimeMs)
                .collect(Collectors.toList()));
        dto.setUnifiedReview(records.stream()
                .filter(r -> "unified_review".equals(r.getStudyMode()) && r.getReactionTimeMs() != null)
                .map(QuizRecord::getReactionTimeMs)
                .collect(Collectors.toList()));
        return dto;
    }

    public List<WeakWordDTO> getWeakWords(Long userId) {
        List<Long> wordIds = userWordBindRepository.findWeakByUserId(userId).stream()
                .limit(30)
                .map(b -> b.getWordId())
                .collect(Collectors.toList());
        if (wordIds.isEmpty()) return Collections.emptyList();
        Map<Long, String> wordTextMap = wordRepository.findAllById(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Word::getWord));
        return userWordBindRepository.findWeakByUserId(userId).stream()
                .limit(30)
                .map(b -> {
                    WeakWordDTO dto = new WeakWordDTO();
                    dto.setWordId(b.getWordId());
                    dto.setWord(wordTextMap.getOrDefault(b.getWordId(), "unknown"));
                    dto.setConsecutiveErrors(b.getConsecutiveErrors() != null ? b.getConsecutiveErrors() : 0);
                    dto.setDifficulty(b.getDifficulty() != null ? b.getDifficulty() : 5.0);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // --- Helpers ---

    private int calculateStreak(List<LocalDate> studyDates) {
        if (studyDates == null || studyDates.isEmpty()) return 0;
        List<LocalDate> sorted = studyDates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        if (sorted.isEmpty()) return 0;

        LocalDate today = LocalDate.now();
        LocalDate expected;
        if (sorted.get(0).equals(today)) {
            expected = today;
        } else if (sorted.get(0).equals(today.minusDays(1))) {
            expected = today.minusDays(1);
        } else {
            return 0;
        }

        int streak = 0;
        for (LocalDate d : sorted) {
            if (d.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }
}
