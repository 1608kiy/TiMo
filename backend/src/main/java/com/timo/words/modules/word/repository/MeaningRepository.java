package com.timo.words.modules.word.repository;

import com.timo.words.modules.word.entity.Meaning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeaningRepository extends JpaRepository<Meaning, Long> {
    List<Meaning> findByWordIdOrderBySortOrder(Long wordId);
}
