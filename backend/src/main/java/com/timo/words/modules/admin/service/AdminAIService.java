package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.admin.entity.AdminOperationLog;
import com.timo.words.modules.admin.entity.AiCallLog;
import com.timo.words.modules.admin.entity.AiProviderConfig;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.AiCallLogRepository;
import com.timo.words.modules.admin.repository.AiProviderConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAIService {

    private final AiProviderConfigRepository providerConfigRepository;
    private final AiCallLogRepository callLogRepository;
    private final AdminOperationLogRepository operationLogRepository;

    public List<AiProviderConfig> listProviders() {
        return providerConfigRepository.findAll();
    }

    public AiProviderConfig createProvider(AiProviderConfig config, Long operatorId, HttpServletRequest request) {
        AiProviderConfig saved = providerConfigRepository.save(config);
        logOperation(operatorId, "AI_CONFIG_CREATE", "ai_provider", saved.getId(),
                "新增厂商: " + config.getDisplayName(), request);
        return saved;
    }

    public AiProviderConfig updateProvider(Long id, AiProviderConfig config, Long operatorId, HttpServletRequest request) {
        AiProviderConfig existing = providerConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
        existing.setProviderName(config.getProviderName());
        existing.setDisplayName(config.getDisplayName());
        existing.setBaseUrl(config.getBaseUrl());
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            existing.setApiKey(config.getApiKey());
        }
        existing.setModel(config.getModel());
        existing.setMaxTokens(config.getMaxTokens());
        existing.setTemperature(config.getTemperature());
        AiProviderConfig saved = providerConfigRepository.save(existing);
        logOperation(operatorId, "AI_CONFIG_UPDATE", "ai_provider", id,
                "更新厂商: " + config.getDisplayName(), request);
        return saved;
    }

    @Transactional
    public void activateProvider(Long id, Long operatorId, HttpServletRequest request) {
        providerConfigRepository.findAll().forEach(p -> {
            p.setIsActive(p.getId().equals(id));
            providerConfigRepository.save(p);
        });
        AiProviderConfig config = providerConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
        logOperation(operatorId, "AI_CONFIG_ACTIVATE", "ai_provider", id,
                "启用厂商: " + config.getDisplayName(), request);
    }

    public void deleteProvider(Long id, Long operatorId, HttpServletRequest request) {
        AiProviderConfig config = providerConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
        providerConfigRepository.deleteById(id);
        logOperation(operatorId, "AI_CONFIG_DELETE", "ai_provider", id,
                "删除厂商: " + config.getDisplayName(), request);
    }

    public AiProviderConfig getActiveProvider() {
        return providerConfigRepository.findByIsActiveTrue().orElse(null);
    }

    public Page<AiCallLog> listLogs(String status, int page, int size) {
        if (status != null && !status.isBlank()) {
            return callLogRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
        }
        return callLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Map<String, Object> getStats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        long totalCalls = callLogRepository.countSince(since);
        long successCalls = callLogRepository.countByStatusSince("SUCCESS", since);
        long totalTokens = callLogRepository.sumTokensSince(since);
        double successRate = totalCalls == 0 ? 1.0 : (double) successCalls / totalCalls;

        return Map.of(
                "totalCalls", totalCalls,
                "successCalls", successCalls,
                "totalTokens", totalTokens,
                "successRate", successRate,
                "dailyStats", callLogRepository.dailyStatsSince(since)
        );
    }

    public void saveCallLog(AiCallLog log) {
        callLogRepository.save(log);
    }

    private void logOperation(Long adminId, String type, String targetType, Long targetId, String detail, HttpServletRequest request) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(adminId);
        log.setOperationType(type);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIpAddress(request.getRemoteAddr());
        operationLogRepository.save(log);
    }
}
