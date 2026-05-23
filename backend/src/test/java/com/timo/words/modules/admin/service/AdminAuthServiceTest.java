package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.infrastructure.security.JwtUtil;
import com.timo.words.infrastructure.security.TokenBlacklistService;
import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private AdminOperationLogRepository operationLogRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private HttpServletRequest request;

    @InjectMocks private AdminAuthService adminAuthService;

    @Test
    void verifySecret_validSecret_upgradesUser() {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("admin_secret");
        config.setConfigValue("mysecret");
        when(systemConfigRepository.findById("admin_secret")).thenReturn(Optional.of(config));

        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setRole("USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(1L, "user@test.com", "ADMIN")).thenReturn("token123");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> result = adminAuthService.verifySecret("mysecret", 1L, request);

        assertEquals("token123", result.get("token"));
        assertEquals("ADMIN", result.get("role"));
        verify(userRepository).save(user);
    }

    @Test
    void verifySecret_validSecret_noUpgradeForAdmin() {
        SystemConfig config = new SystemConfig();
        config.setConfigValue("mysecret");
        when(systemConfigRepository.findById("admin_secret")).thenReturn(Optional.of(config));

        User user = new User();
        user.setId(1L);
        user.setEmail("admin@test.com");
        user.setRole("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(1L, "admin@test.com", "ADMIN")).thenReturn("token456");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> result = adminAuthService.verifySecret("mysecret", 1L, request);

        assertEquals("ADMIN", result.get("role"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifySecret_invalidSecret_throwsException() {
        SystemConfig config = new SystemConfig();
        config.setConfigValue("correct");
        when(systemConfigRepository.findById("admin_secret")).thenReturn(Optional.of(config));

        assertThrows(BusinessException.class, () ->
                adminAuthService.verifySecret("wrong", 1L, request));
    }

    @Test
    void verifySecret_noConfig_throwsException() {
        when(systemConfigRepository.findById("admin_secret")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                adminAuthService.verifySecret("any", 1L, request));
    }

    @Test
    void impersonate_superAdmin_createsToken() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        admin.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        User target = new User();
        target.setId(2L);
        target.setEmail("target@test.com");
        target.setNickname("Target");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(jwtUtil.generateImpersonateToken(1L, "admin@test.com", "SUPER_ADMIN", 2L)).thenReturn("impersonate_token");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> result = adminAuthService.impersonate(2L, 1L, request);

        assertEquals("impersonate_token", result.get("token"));
        assertNotNull(result.get("targetUser"));
    }

    @Test
    void impersonate_nonSuperAdmin_throwsException() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(BusinessException.class, () ->
                adminAuthService.impersonate(2L, 1L, request));
    }

    @Test
    void exitImpersonate_blacklistsToken() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(jwtUtil.isTokenValid("validtoken")).thenReturn(true);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminAuthService.exitImpersonate("Bearer validtoken", 1L, request);

        verify(tokenBlacklistService).blacklist("validtoken");
    }

    @Test
    void exitImpersonate_nullToken_noBlacklist() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminAuthService.exitImpersonate(null, 1L, request);

        verify(tokenBlacklistService, never()).blacklist(any());
    }
}
