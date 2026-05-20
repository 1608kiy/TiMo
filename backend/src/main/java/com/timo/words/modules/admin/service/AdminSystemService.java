package com.timo.words.modules.admin.service;

import com.timo.words.modules.admin.entity.AdminOperationLog;
import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSystemService {

    private final SystemConfigRepository configRepository;
    private final AdminOperationLogRepository operationLogRepository;

    public List<SystemConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    public SystemConfig getConfig(String key) {
        return configRepository.findById(key).orElse(null);
    }

    public String getConfigValue(String key, String defaultValue) {
        return configRepository.findById(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    public SystemConfig updateConfig(String key, String value, Long operatorId, HttpServletRequest request) {
        SystemConfig config = configRepository.findById(key).orElse(new SystemConfig());
        String oldValue = config.getConfigValue();
        config.setConfigKey(key);
        config.setConfigValue(value);
        SystemConfig saved = configRepository.save(config);

        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(operatorId);
        log.setOperationType("SYSTEM_CONFIG_UPDATE");
        log.setTargetType("system_config");
        log.setDetail(key + ": " + oldValue + " -> " + value);
        log.setIpAddress(request.getRemoteAddr());
        operationLogRepository.save(log);

        return saved;
    }

    public void batchUpdate(Map<String, String> configs, Long operatorId, HttpServletRequest request) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            updateConfig(entry.getKey(), entry.getValue(), operatorId, request);
        }
    }
}
