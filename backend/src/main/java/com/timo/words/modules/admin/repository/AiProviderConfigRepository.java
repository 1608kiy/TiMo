package com.timo.words.modules.admin.repository;

import com.timo.words.modules.admin.entity.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {
    Optional<AiProviderConfig> findByIsActiveTrue();

    @Modifying
    @Query("UPDATE AiProviderConfig p SET p.isActive = false WHERE p.isActive = true")
    void deactivateAll();

    @Modifying
    @Query("UPDATE AiProviderConfig p SET p.isActive = (p.id = :id)")
    void activateById(@Param("id") Long id);
}
