package com.timo.words.modules.word.repository;

import com.timo.words.modules.word.entity.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long>, JpaSpecificationExecutor<Word> {

    @Query("SELECT DISTINCT w FROM Word w LEFT JOIN w.meanings m WHERE (:keyword IS NULL OR LOWER(w.word) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND (:examType IS NULL OR w.examType = :examType) AND (:pos IS NULL OR LOWER(m.partOfSpeech) = LOWER(:pos))")
    Page<Word> searchByKeywordAndExamType(@Param("keyword") String keyword, @Param("examType") String examType, @Param("pos") String pos, Pageable pageable);

    List<Word> findByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT w FROM Word w WHERE LOWER(w.word) IN :words")
    List<Word> findByWordIn(@Param("words") List<String> words);

    long countByExamType(String examType);

    List<Word> findByExamTypeIn(List<String> examTypes);

    @Query("SELECT w.examType, COUNT(w) FROM Word w GROUP BY w.examType")
    List<Object[]> countByExamTypeGroup();
}
