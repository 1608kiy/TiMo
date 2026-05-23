package com.timo.words.modules.admin.service;

import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSystemServiceTest {

    @Mock private SystemConfigRepository configRepository;
    @Mock private AdminOperationLogRepository operationLogRepository;
    @Mock private HttpServletRequest request;

    @InjectMocks private AdminSystemService adminSystemService;

    @Test
    void getAllConfigs_returnsAll() {
        SystemConfig c1 = new SystemConfig();
        c1.setConfigKey("key1");
        when(configRepository.findAll()).thenReturn(List.of(c1));

        List<SystemConfig> result = adminSystemService.getAllConfigs();

        assertEquals(1, result.size());
    }

    @Test
    void getConfig_existingKey_returnsConfig() {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("admin_secret");
        config.setConfigValue("value");
        when(configRepository.findById("admin_secret")).thenReturn(Optional.of(config));

        SystemConfig result = adminSystemService.getConfig("admin_secret");

        assertNotNull(result);
        assertEquals("value", result.getConfigValue());
    }

    @Test
    void getConfig_missingKey_returnsNull() {
        when(configRepository.findById("missing")).thenReturn(Optional.empty());

        SystemConfig result = adminSystemService.getConfig("missing");

        assertNull(result);
    }

    @Test
    void getConfigValue_existingKey_returnsValue() {
        SystemConfig config = new SystemConfig();
        config.setConfigValue("1.0");
        when(configRepository.findById("fsrs_default_stability")).thenReturn(Optional.of(config));

        String result = adminSystemService.getConfigValue("fsrs_default_stability", "2.0");

        assertEquals("1.0", result);
    }

    @Test
    void getConfigValue_missingKey_returnsDefault() {
        when(configRepository.findById("missing")).thenReturn(Optional.empty());

        String result = adminSystemService.getConfigValue("missing", "default");

        assertEquals("default", result);
    }

    @Test
    void updateConfig_existingKey_updatesAndLogs() {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("key1");
        config.setConfigValue("old");
        when(configRepository.findById("key1")).thenReturn(Optional.of(config));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        SystemConfig result = adminSystemService.updateConfig("key1", "new", 1L, request);

        assertEquals("new", result.getConfigValue());
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateConfig_newKey_createsAndLogs() {
        when(configRepository.findById("newkey")).thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        SystemConfig result = adminSystemService.updateConfig("newkey", "value", 1L, request);

        assertEquals("newkey", result.getConfigKey());
        assertEquals("value", result.getConfigValue());
        verify(operationLogRepository).save(any());
    }

    @Test
    void batchUpdate_updatesMultipleConfigs() {
        when(configRepository.findById(any())).thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminSystemService.batchUpdate(Map.of("k1", "v1", "k2", "v2"), 1L, request);

        verify(configRepository, times(2)).save(any());
        verify(operationLogRepository, times(2)).save(any());
    }
}
