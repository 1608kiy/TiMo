package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.infrastructure.security.JwtUtil;
import com.timo.words.infrastructure.security.TokenBlacklistService;
import com.timo.words.modules.admin.entity.AdminOperationLog;
import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AdminOperationLogRepository operationLogRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public Map<String, Object> verifySecret(String secret, Long userId, HttpServletRequest request) {
        SystemConfig config = systemConfigRepository.findById("admin_secret").orElse(null);
        if (config == null || !config.getConfigValue().equals(secret)) {
            throw new BusinessException(ResultCode.ADMIN_SECRET_INVALID);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        boolean wasUpgraded = !"ADMIN".equals(user.getRole()) && !"SUPER_ADMIN".equals(user.getRole());
        if (wasUpgraded) {
            user.setRole("ADMIN");
            userRepository.save(user);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        logOperation(userId, user.getEmail(), "ADMIN_SECRET_VERIFY", "user", userId,
                "密钥验证" + (wasUpgraded ? "，账号已升级为管理员" : ""), request);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", user.getRole());
        return result;
    }

    public Map<String, Object> impersonate(Long targetUserId, Long adminId, HttpServletRequest request) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!"SUPER_ADMIN".equals(admin.getRole())) {
            throw new BusinessException(ResultCode.SUPER_ADMIN_REQUIRED);
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        String token = jwtUtil.generateImpersonateToken(adminId, admin.getEmail(), admin.getRole(), targetUserId);

        logOperation(adminId, admin.getEmail(), "IMPERSONATE", "user", targetUserId,
                "模拟登录用户: " + target.getEmail(), request);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("targetUser", Map.of(
                "id", target.getId(),
                "email", target.getEmail(),
                "nickname", target.getNickname() != null ? target.getNickname() : ""
        ));
        result.put("expiresIn", 300);
        return result;
    }

    public void exitImpersonate(String token, Long adminId, HttpServletRequest request) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token != null && jwtUtil.isTokenValid(token)) {
            tokenBlacklistService.blacklist(token);
        }

        User admin = userRepository.findById(adminId).orElse(null);
        if (admin != null) {
            logOperation(adminId, admin.getEmail(), "EXIT_IMPERSONATE", null, null,
                    "退出模拟登录", request);
        }
    }

    private void logOperation(Long adminId, String adminEmail, String type, String targetType, Long targetId, String detail, HttpServletRequest request) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(adminId);
        log.setAdminEmail(adminEmail);
        log.setOperationType(type);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIpAddress(request.getRemoteAddr());
        operationLogRepository.save(log);
    }
}
