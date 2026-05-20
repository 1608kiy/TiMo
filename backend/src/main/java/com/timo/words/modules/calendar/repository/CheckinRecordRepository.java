package com.timo.words.modules.calendar.repository;

import com.timo.words.modules.calendar.entity.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {

    Optional<CheckinRecord> findByUserIdAndCheckinDate(Long userId, LocalDate date);

    List<CheckinRecord> findByUserIdAndCheckinDateBetween(Long userId, LocalDate start, LocalDate end);

    List<CheckinRecord> findByUserIdOrderByCheckinDateDesc(Long userId);
}
