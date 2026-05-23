package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.admin.entity.AiCallLog;
import com.timo.words.modules.admin.entity.AiProviderConfig;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.AiCallLogRepository;
import com.timo.words.modules.admin.repository.AiProviderConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAIServiceTest {

    @Mock private AiProviderConfigRepository providerConfigRepository;
    @Mock private AiCallLogRepository callLogRepository;
    @Mock private AdminOperationLogRepository operationLogRepository;
    @Mock private HttpServletRequest request;

    @InjectMocks private AdminAIService adminAIService;

    @Test
    void listProviders_returnsAll() {
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderName("deepseek");
        when(providerConfigRepository.findAll()).thenReturn(List.of(config));

        List<AiProviderConfig> result = adminAIService.listProviders();

        assertEquals(1, result.size());
    }

    @Test
    void createProvider_savesAndLogs() {
        AiProviderConfig config = new AiProviderConfig();
        config.setDisplayName("DeepSeek");
        when(providerConfigRepository.save(config)).thenAnswer(inv -> {
            AiProviderConfig c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        AiProviderConfig result = adminAIService.createProvider(config, 1L, request);

        assertNotNull(result.getId());
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateProvider_existing_updatesAndLogs() {
        AiProviderConfig existing = new AiProviderConfig();
        existing.setId(1L);
        existing.setProviderName("old");
        existing.setDisplayName("Old");
        existing.setBaseUrl("http://old.com");
        existing.setModel("old-model");
        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(providerConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        AiProviderConfig update = new AiProviderConfig();
        update.setProviderName("new");
        update.setDisplayName("New");
        update.setBaseUrl("http://new.com");
        update.setModel("new-model");
        update.setApiKey("new-key");

        AiProviderConfig result = adminAIService.updateProvider(1L, update, 1L, request);

        assertEquals("new", result.getProviderName());
        assertEquals("new-key", result.getApiKey());
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateProvider_notFound_throwsException() {
        when(providerConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                adminAIService.updateProvider(99L, new AiProviderConfig(), 1L, request));
    }

    @Test
    void activateProvider_callsBatchUpdate() {
        AiProviderConfig config = new AiProviderConfig();
        config.setId(1L);
        config.setDisplayName("DeepSeek");
        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminAIService.activateProvider(1L, 1L, request);

        verify(providerConfigRepository).activateById(1L);
        verify(operationLogRepository).save(any());
    }

    @Test
    void deleteProvider_existing_deletesAndLogs() {
        AiProviderConfig config = new AiProviderConfig();
        config.setId(1L);
        config.setDisplayName("DeepSeek");
        when(providerConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminAIService.deleteProvider(1L, 1L, request);

        verify(providerConfigRepository).deleteById(1L);
        verify(operationLogRepository).save(any());
    }

    @Test
    void deleteProvider_notFound_throwsException() {
        when(providerConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                adminAIService.deleteProvider(99L, 1L, request));
    }

    @Test
    void getActiveProvider_returnsProvider() {
        AiProviderConfig config = new AiProviderConfig();
        config.setProviderName("deepseek");
        when(providerConfigRepository.findByIsActiveTrue()).thenReturn(Optional.of(config));

        AiProviderConfig result = adminAIService.getActiveProvider();

        assertNotNull(result);
        assertEquals("deepseek", result.getProviderName());
    }

    @Test
    void getActiveProvider_noneActive_returnsNull() {
        when(providerConfigRepository.findByIsActiveTrue()).thenReturn(Optional.empty());

        AiProviderConfig result = adminAIService.getActiveProvider();

        assertNull(result);
    }

    @Test
    void listLogs_withStatus_filtersByStatus() {
        Page<AiCallLog> page = new PageImpl<>(List.of());
        when(callLogRepository.findByStatusOrderByCreatedAtDesc(eq("SUCCESS"), any(PageRequest.class)))
                .thenReturn(page);

        Page<AiCallLog> result = adminAIService.listLogs("SUCCESS", 0, 15);

        assertNotNull(result);
    }

    @Test
    void listLogs_noStatus_returnsAll() {
        Page<AiCallLog> page = new PageImpl<>(List.of());
        when(callLogRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                .thenReturn(page);

        Page<AiCallLog> result = adminAIService.listLogs(null, 0, 15);

        assertNotNull(result);
    }

    @Test
    void getStats_calculatesCorrectly() {
        when(callLogRepository.countSince(any())).thenReturn(100L);
        when(callLogRepository.countByStatusSince(eq("SUCCESS"), any())).thenReturn(90L);
        when(callLogRepository.sumTokensSince(any())).thenReturn(50000L);
        when(callLogRepository.dailyStatsSince(any())).thenReturn(List.of());

        Map<String, Object> stats = adminAIService.getStats(7);

        assertEquals(100L, stats.get("totalCalls"));
        assertEquals(90L, stats.get("successCalls"));
        assertEquals(50000L, stats.get("totalTokens"));
        assertEquals(0.9, (double) stats.get("successRate"), 0.01);
    }

    @Test
    void getStats_zeroCalls_returnsOneSuccessRate() {
        when(callLogRepository.countSince(any())).thenReturn(0L);
        when(callLogRepository.countByStatusSince(eq("SUCCESS"), any())).thenReturn(0L);
        when(callLogRepository.sumTokensSince(any())).thenReturn(0L);
        when(callLogRepository.dailyStatsSince(any())).thenReturn(List.of());

        Map<String, Object> stats = adminAIService.getStats(7);

        assertEquals(1.0, (double) stats.get("successRate"));
    }

    @Test
    void saveCallLog_savesLog() {
        AiCallLog log = new AiCallLog();
        adminAIService.saveCallLog(log);
        verify(callLogRepository).save(log);
    }
}
