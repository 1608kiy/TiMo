package com.timo.words.modules.admin.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.admin.entity.AiCallLog;
import com.timo.words.modules.admin.entity.AiProviderConfig;
import com.timo.words.modules.admin.service.AdminAIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI配置管理")
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAIController {

    private final AdminAIService adminAIService;

    @Operation(summary = "获取所有厂商配置")
    @GetMapping("/providers")
    public Result<List<AiProviderConfig>> listProviders() {
        return Result.success(adminAIService.listProviders());
    }

    @Operation(summary = "新增厂商配置")
    @PostMapping("/providers")
    public Result<AiProviderConfig> createProvider(@RequestBody AiProviderConfig config,
                                                   Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        return Result.success(adminAIService.createProvider(config, operatorId, request));
    }

    @Operation(summary = "修改厂商配置")
    @PutMapping("/providers/{id}")
    public Result<AiProviderConfig> updateProvider(@PathVariable Long id, @RequestBody AiProviderConfig config,
                                                   Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        return Result.success(adminAIService.updateProvider(id, config, operatorId, request));
    }

    @Operation(summary = "启用厂商")
    @PutMapping("/providers/{id}/activate")
    public Result<Void> activateProvider(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        adminAIService.activateProvider(id, operatorId, request);
        return Result.success(null);
    }

    @Operation(summary = "删除厂商配置")
    @DeleteMapping("/providers/{id}")
    public Result<Void> deleteProvider(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        adminAIService.deleteProvider(id, operatorId, request);
        return Result.success(null);
    }

    @Operation(summary = "AI调用日志")
    @GetMapping("/logs")
    public Result<Page<AiCallLog>> listLogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminAIService.listLogs(status, page, size));
    }

    @Operation(summary = "AI调用统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "7") int days) {
        return Result.success(adminAIService.getStats(days));
    }
}
