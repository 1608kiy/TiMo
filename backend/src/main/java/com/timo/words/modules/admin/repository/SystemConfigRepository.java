package com.timo.words.modules.admin.repository;

import com.timo.words.modules.admin.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
}
