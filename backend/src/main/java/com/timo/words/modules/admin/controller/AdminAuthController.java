package com.timo.words.modules.admin.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.admin.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理员认证")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "验证管理员密钥")
    @PostMapping("/verify-secret")
    public Result<Map<String, Object>> verifySecret(@RequestBody Map<String, String> body, Authentication auth, HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(adminAuthService.verifySecret(body.get("secret"), userId, request));
    }

    @Operation(summary = "模拟登录")
    @PostMapping("/impersonate/{targetUserId}")
    public Result<Map<String, Object>> impersonate(@PathVariable Long targetUserId, Authentication auth, HttpServletRequest request) {
        Long adminId = (Long) auth.getPrincipal();
        return Result.success(adminAuthService.impersonate(targetUserId, adminId, request));
    }

    @Operation(summary = "退出模拟登录")
    @PostMapping("/exit-impersonate")
    public Result<Void> exitImpersonate(Authentication auth, HttpServletRequest request) {
        Long adminId = (Long) auth.getPrincipal();
        String token = request.getHeader("Authorization");
        adminAuthService.exitImpersonate(token, adminId, request);
        return Result.success(null);
    }
}
