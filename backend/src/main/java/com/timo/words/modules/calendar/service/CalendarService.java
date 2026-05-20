package com.timo.words.modules.calendar.service;

import com.timo.words.modules.calendar.entity.CheckinRecord;
import com.timo.words.modules.calendar.repository.CheckinRecordRepository;
import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CheckinRecordRepository checkinRecordRepository;
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

    @Data
    public static class CheckinRequest {
        private Integer studyMinutes;
        private Integer wordsStudied;
    }

    public MonthlyDTO getMonthly(Long userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<CheckinRecord> checkins = checkinRecordRepository.findByUserIdAndCheckinDateBetween(userId, start, end);
        Map<LocalDate, CheckinRecord> checkinMap = checkins.stream()
                .collect(Collectors.toMap(CheckinRecord::getCheckinDate, c -> c, (a, b) -> a));

        // Get quiz records for the month
        List<QuizRecord> records = quizRecordRepository.findByUserIdAndCreatedAtBetween(
                userId, start.atStartOfDay(), end.atTime(LocalTime.MAX));
        Map<LocalDate, List<QuizRecord>> recordsByDay = records.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));

        List<DayDTO> days = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayDTO day = new DayDTO();
            day.setDate(current.toString());
            CheckinRecord ci = checkinMap.get(current);
            day.setCheckedIn(ci != null);
            day.setStudyMinutes(ci != null ? ci.getStudyMinutes() : 0);
            day.setWordsStudied(ci != null ? ci.getWordsStudied() : 0);

            List<QuizRecord> dayRecords = recordsByDay.getOrDefault(current, List.of());
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
        dto.setTotalCheckinDays(checkins.size());
        return dto;
    }

    @Transactional
    public CheckinRecord checkin(Long userId, CheckinRequest req) {
        LocalDate today = LocalDate.now();
        try {
            CheckinRecord existing = checkinRecordRepository.findByUserIdAndCheckinDate(userId, today).orElse(null);

            if (existing != null) {
                existing.setStudyMinutes(req.getStudyMinutes() != null ? req.getStudyMinutes() : existing.getStudyMinutes());
                existing.setWordsStudied(req.getWordsStudied() != null ? req.getWordsStudied() : existing.getWordsStudied());
                return checkinRecordRepository.save(existing);
            }

            CheckinRecord record = new CheckinRecord();
            record.setUserId(userId);
            record.setCheckinDate(today);
            record.setStudyMinutes(req.getStudyMinutes() != null ? req.getStudyMinutes() : 0);
            record.setWordsStudied(req.getWordsStudied() != null ? req.getWordsStudied() : 0);
            return checkinRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            CheckinRecord existing = checkinRecordRepository.findByUserIdAndCheckinDate(userId, today)
                    .orElseThrow(() -> e);
            existing.setStudyMinutes(req.getStudyMinutes() != null ? req.getStudyMinutes() : existing.getStudyMinutes());
            existing.setWordsStudied(req.getWordsStudied() != null ? req.getWordsStudied() : existing.getWordsStudied());
            return checkinRecordRepository.save(existing);
        }
    }

    @Transactional
    public void autoCheckin(Long userId, int wordsStudied) {
        LocalDate today = LocalDate.now();
        CheckinRecord existing = checkinRecordRepository.findByUserIdAndCheckinDate(userId, today).orElse(null);

        if (existing != null) {
            existing.setWordsStudied(existing.getWordsStudied() + wordsStudied);
            checkinRecordRepository.save(existing);
        } else {
            CheckinRecord record = new CheckinRecord();
            record.setUserId(userId);
            record.setCheckinDate(today);
            record.setWordsStudied(wordsStudied);
            checkinRecordRepository.save(record);
        }
    }
}
