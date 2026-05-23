package com.timo.words.modules.admin.service;

import com.timo.words.modules.admin.entity.AiProviderConfig;
import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.repository.AiProviderConfigRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigRepository systemConfigRepository;
    private final AiProviderConfigRepository aiProviderConfigRepository;

    @Override
    public void run(String... args) {
        initSuperAdmin();
        fixExistingUsers();
        initSystemConfigs();
        initDefaultAiProvider();
    }

    private void fixExistingUsers() {
        userRepository.findAll().forEach(user -> {
            boolean changed = false;
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole("USER");
                changed = true;
            }
            if (user.getStatus() == null || user.getStatus().isBlank()) {
                user.setStatus("ACTIVE");
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        });
    }

    private void initSuperAdmin() {
        String adminEmail = System.getenv().getOrDefault("ADMIN_EMAIL", "tangjunhua@timo.com");
        String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "123456789");
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setNickname("超级管理员");
            admin.setRole("SUPER_ADMIN");
            admin.setStatus("ACTIVE");
            userRepository.save(admin);
            log.info("超级管理员账号已创建: {}", adminEmail);
        }
    }

    private void initSystemConfigs() {
        String adminSecret = System.getenv().getOrDefault("ADMIN_SECRET", "16608117290hj@HJ");
        initConfig("admin_secret", adminSecret, "管理员密钥");
        initConfig("fsrs_default_stability", "1.0", "FSRS 默认稳定性");
        initConfig("fsrs_default_difficulty", "5.0", "FSRS 默认难度");
        initConfig("cold_start_mu", "8.0", "冷启动 μ 值（秒）");
        initConfig("cold_start_sigma", "3.0", "冷启动 σ 值（秒）");
        initConfig("df_theta1", "0.3", "λ_rt 阈值");
        initConfig("df_theta2", "0.5", "λ_acc 阈值");
        initConfig("fatigue_threshold_minutes", "20", "疲劳检测阈值（分钟）");
        initConfig("circuit_breaker_threshold", "3", "熔断失败次数");
        initConfig("circuit_breaker_reset_ms", "60000", "熔断恢复时间（毫秒）");
    }

    private void initConfig(String key, String value, String description) {
        if (systemConfigRepository.findById(key).isEmpty()) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            systemConfigRepository.save(config);
        }
    }

    private void initDefaultAiProvider() {
        String defaultApiKey = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "sk-090bfa7c2e4e4ed5a21694993bfd8dd6");
        if (aiProviderConfigRepository.count() == 0) {
            AiProviderConfig config = new AiProviderConfig();
            config.setProviderName("deepseek");
            config.setDisplayName("DeepSeek");
            config.setBaseUrl("https://api.deepseek.com");
            config.setApiKey(defaultApiKey);
            config.setModel("deepseek-v4-flash");
            config.setMaxTokens(2048);
            config.setTemperature(0.7);
            config.setIsActive(true);
            aiProviderConfigRepository.save(config);
            log.info("默认 AI 厂商配置已创建: DeepSeek");
        } else {
            aiProviderConfigRepository.findAll().forEach(config -> {
                if (config.getApiKey() != null && config.getApiKey().contains("${")) {
                    config.setApiKey(defaultApiKey);
                    aiProviderConfigRepository.save(config);
                    log.info("已修复厂商 {} 的 API Key", config.getDisplayName());
                }
            });
        }
    }
}
