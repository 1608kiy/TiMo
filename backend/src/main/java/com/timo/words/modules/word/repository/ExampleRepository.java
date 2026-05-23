package com.timo.words.modules.word.repository;

import com.timo.words.modules.word.entity.Example;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExampleRepository extends JpaRepository<Example, Long> {

    List<Example> findByWordIdOrderByIdAsc(Long wordId);
}
