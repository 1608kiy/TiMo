package com.timo.words.modules.admin.repository;

import com.timo.words.modules.admin.entity.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {
    Optional<AiProviderConfig> findByIsActiveTrue();
}
