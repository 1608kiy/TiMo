package com.timo.words.modules.calendar.service;

import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private QuizRecordRepository quizRecordRepository;
    @InjectMocks private CalendarService calendarService;

    @Test
    void getMonthly_emptyMonth() {
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        CalendarService.MonthlyDTO result = calendarService.getMonthly(1L, 2026, 5);

        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonth());
        assertEquals(0, result.getTotalCheckinDays());
        assertEquals(31, result.getDays().size());
        assertFalse(result.getDays().get(0).isCheckedIn());
    }

    @Test
    void getMonthly_studyDayDerivedFromQuizRecords() {
        QuizRecord r = new QuizRecord();
        r.setUserId(1L);
        r.setWordId(42L);
        r.setGrade(3.5);
        r.setReactionTimeMs(60_000);
        r.setCreatedAt(LocalDateTime.of(2026, 5, 14, 10, 0));

        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(List.of(r));

        CalendarService.MonthlyDTO result = calendarService.getMonthly(1L, 2026, 5);

        assertEquals(1, result.getTotalCheckinDays());
        CalendarService.DayDTO day14 = result.getDays().stream()
                .filter(d -> d.getDate().equals("2026-05-14"))
                .findFirst().orElseThrow();
        assertTrue(day14.isCheckedIn());
        assertEquals(1, day14.getWordsStudied());
        assertEquals(1, day14.getTotalRecords());
    }
}
