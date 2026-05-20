package com.timo.words.modules.calendar.service;

import com.timo.words.modules.calendar.entity.CheckinRecord;
import com.timo.words.modules.calendar.repository.CheckinRecordRepository;
import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private CheckinRecordRepository checkinRecordRepository;
    @Mock private QuizRecordRepository quizRecordRepository;
    @InjectMocks private CalendarService calendarService;

    @Test
    void getMonthly_emptyMonth() {
        when(checkinRecordRepository.findByUserIdAndCheckinDateBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        CalendarService.MonthlyDTO result = calendarService.getMonthly(1L, 2026, 5);

        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonth());
        assertEquals(0, result.getTotalCheckinDays());
        // May has 31 days
        assertEquals(31, result.getDays().size());
        assertFalse(result.getDays().get(0).isCheckedIn());
    }

    @Test
    void getMonthly_withCheckin() {
        CheckinRecord record = new CheckinRecord();
        record.setUserId(1L);
        record.setCheckinDate(LocalDate.of(2026, 5, 14));
        record.setStudyMinutes(30);
        record.setWordsStudied(20);

        when(checkinRecordRepository.findByUserIdAndCheckinDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(record));
        when(quizRecordRepository.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        CalendarService.MonthlyDTO result = calendarService.getMonthly(1L, 2026, 5);

        assertEquals(1, result.getTotalCheckinDays());
        // Find May 14
        CalendarService.DayDTO day14 = result.getDays().stream()
                .filter(d -> d.getDate().equals("2026-05-14"))
                .findFirst().orElseThrow();
        assertTrue(day14.isCheckedIn());
        assertEquals(30, day14.getStudyMinutes());
        assertEquals(20, day14.getWordsStudied());
    }

    @Test
    void checkin_newRecord() {
        when(checkinRecordRepository.findByUserIdAndCheckinDate(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(checkinRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CalendarService.CheckinRequest req = new CalendarService.CheckinRequest();
        req.setStudyMinutes(45);
        req.setWordsStudied(30);

        CheckinRecord result = calendarService.checkin(1L, req);

        assertNotNull(result);
        assertEquals(45, result.getStudyMinutes());
        assertEquals(30, result.getWordsStudied());
        assertEquals(LocalDate.now(), result.getCheckinDate());
    }

    @Test
    void checkin_existingRecord_updates() {
        CheckinRecord existing = new CheckinRecord();
        existing.setId(1L);
        existing.setUserId(1L);
        existing.setCheckinDate(LocalDate.now());
        existing.setStudyMinutes(20);
        existing.setWordsStudied(10);

        when(checkinRecordRepository.findByUserIdAndCheckinDate(eq(1L), any()))
                .thenReturn(Optional.of(existing));
        when(checkinRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CalendarService.CheckinRequest req = new CalendarService.CheckinRequest();
        req.setStudyMinutes(60);

        CheckinRecord result = calendarService.checkin(1L, req);

        assertEquals(60, result.getStudyMinutes());
        assertEquals(10, result.getWordsStudied()); // unchanged
    }

    @Test
    void autoCheckin_newRecord() {
        when(checkinRecordRepository.findByUserIdAndCheckinDate(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(checkinRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        calendarService.autoCheckin(1L, 15);

        verify(checkinRecordRepository).save(argThat(r ->
                r.getWordsStudied() == 15 && r.getCheckinDate().equals(LocalDate.now())));
    }

    @Test
    void autoCheckin_existingRecord_addsWords() {
        CheckinRecord existing = new CheckinRecord();
        existing.setId(1L);
        existing.setWordsStudied(10);

        when(checkinRecordRepository.findByUserIdAndCheckinDate(eq(1L), any()))
                .thenReturn(Optional.of(existing));
        when(checkinRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        calendarService.autoCheckin(1L, 5);

        verify(checkinRecordRepository).save(argThat(r -> r.getWordsStudied() == 15));
    }
}
