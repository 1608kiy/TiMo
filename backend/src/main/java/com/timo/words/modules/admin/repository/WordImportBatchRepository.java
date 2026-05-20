package com.timo.words.modules.admin.repository;

import com.timo.words.modules.admin.entity.WordImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordImportBatchRepository extends JpaRepository<WordImportBatch, Long> {
}
