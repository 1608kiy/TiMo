package com.timo.words.modules.admin.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.service.AdminSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminSystemService systemService;

    @Operation(summary = "获取所有配置")
    @GetMapping("/config")
    public Result<List<SystemConfig>> getAllConfigs() {
        return Result.success(systemService.getAllConfigs());
    }

    @Operation(summary = "获取单个配置")
    @GetMapping("/config/{key}")
    public Result<SystemConfig> getConfig(@PathVariable String key) {
        return Result.success(systemService.getConfig(key));
    }

    @Operation(summary = "更新单个配置")
    @PutMapping("/config/{key}")
    public Result<SystemConfig> updateConfig(@PathVariable String key, @RequestBody Map<String, String> body,
                                             Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        return Result.success(systemService.updateConfig(key, body.get("value"), operatorId, request));
    }

    @Operation(summary = "批量更新配置")
    @PutMapping("/config")
    public Result<Void> batchUpdate(@RequestBody Map<String, String> configs,
                                    Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        systemService.batchUpdate(configs, operatorId, request);
        return Result.success(null);
    }
}
