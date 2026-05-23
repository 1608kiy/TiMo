package com.timo.words.modules.examplan.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.examplan.service.ExamPlanService;
import com.timo.words.modules.examplan.service.ExamPlanService.DailyQuotaDTO;
import com.timo.words.modules.examplan.service.ExamPlanService.DialogRequest;
import com.timo.words.modules.examplan.service.ExamPlanService.DialogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "备考规划模块")
@RestController
@RequestMapping("/api/exam-plan")
@RequiredArgsConstructor
public class ExamPlanController {

    private final ExamPlanService examPlanService;

    @Operation(summary = "开始规划对话")
    @PostMapping("/start-dialog")
    public Result<DialogResponse> startDialog(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(examPlanService.startDialog(userId));
    }

    @Operation(summary = "继续规划对话")
    @PostMapping("/continue-dialog")
    public Result<DialogResponse> continueDialog(
            @RequestBody DialogRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(examPlanService.continueDialog(userId, request.getAnswer()));
    }

    @Operation(summary = "获取当前规划状态")
    @GetMapping("/status")
    public Result<DialogResponse> getStatus(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(examPlanService.getPlanStatus(userId));
    }

    @Operation(summary = "获取今日学习配额（新词/复习目标 vs 已完成）")
    @GetMapping("/daily-quota")
    public Result<DailyQuotaDTO> getDailyQuota(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(examPlanService.getTodayQuota(userId));
    }
}
