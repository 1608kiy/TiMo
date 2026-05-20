package com.timo.words.modules.admin.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.admin.service.AdminUserService;
import com.timo.words.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表")
    @GetMapping
    public Result<Page<User>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminUserService.listUsers(keyword, role, status, page, size));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<User> detail(@PathVariable Long id) {
        return Result.success(adminUserService.getUserDetail(id));
    }

    @Operation(summary = "修改用户角色")
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body,
                                   Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        adminUserService.updateRole(id, body.get("role"), operatorId, request);
        return Result.success(null);
    }

    @Operation(summary = "封禁/解封用户")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
                                     Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        adminUserService.updateStatus(id, body.get("status"), operatorId, request);
        return Result.success(null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        adminUserService.deleteUser(id, operatorId, request);
        return Result.success(null);
    }
}
