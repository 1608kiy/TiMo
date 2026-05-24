package com.timo.words.modules.calendar.service;

import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final QuizRecordRepository quizRecordRepository;

    @Data
    public static class MonthlyDTO {
        private int year;
        private int month;
        private List<DayDTO> days;
        private int totalCheckinDays;
    }

    @Data
    public static class DayDTO {
        private String date;
        private boolean checkedIn;
        private int studyMinutes;
        private int wordsStudied;
        private int totalRecords;
        private double avgGrade;
    }

    public MonthlyDTO getMonthly(Long userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<QuizRecord> records = quizRecordRepository.findByUserIdAndCreatedAtBetween(
                userId, start.atStartOfDay(), end.atTime(LocalTime.MAX));
        Map<LocalDate, List<QuizRecord>> recordsByDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));

        List<DayDTO> days = new ArrayList<>();
        int totalCheckinDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayDTO day = new DayDTO();
            day.setDate(current.toString());

            List<QuizRecord> dayRecords = recordsByDay.getOrDefault(current, List.of());
            boolean checkedIn = !dayRecords.isEmpty();
            day.setCheckedIn(checkedIn);
            if (checkedIn) totalCheckinDays++;

            int wordsStudied = (int) dayRecords.stream()
                    .map(QuizRecord::getWordId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            day.setWordsStudied(wordsStudied);

            long totalMs = dayRecords.stream()
                    .map(QuizRecord::getReactionTimeMs)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            day.setStudyMinutes((int) Math.round(totalMs / 60000.0));

            day.setTotalRecords(dayRecords.size());
            double avg = dayRecords.stream()
                    .mapToDouble(r -> r.getGrade() != null ? r.getGrade() : 0)
                    .average().orElse(0);
            day.setAvgGrade(Math.round(avg * 10.0) / 10.0);

            days.add(day);
            current = current.plusDays(1);
        }

        MonthlyDTO dto = new MonthlyDTO();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setDays(days);
        dto.setTotalCheckinDays(totalCheckinDays);
        return dto;
    }
}
