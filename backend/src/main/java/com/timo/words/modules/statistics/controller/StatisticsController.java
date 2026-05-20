package com.timo.words.modules.statistics.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.statistics.service.StatisticsService;
import com.timo.words.modules.statistics.service.StatisticsService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "统计模块")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "概览数据")
    @GetMapping("/overview")
    public Result<OverviewDTO> getOverview(Authentication auth) {
        return Result.success(statisticsService.getOverview((Long) auth.getPrincipal()));
    }

    @Operation(summary = "记忆保持率趋势")
    @GetMapping("/retention")
    public Result<RetentionDTO> getRetention(Authentication auth,
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(statisticsService.getRetention((Long) auth.getPrincipal(), days));
    }

    @Operation(summary = "遗忘曲线对比")
    @GetMapping("/forgetting-curve")
    public Result<ForgettingCurveDTO> getForgettingCurve(Authentication auth,
            @RequestParam(defaultValue = "14") int days) {
        return Result.success(statisticsService.getForgettingCurve((Long) auth.getPrincipal(), days));
    }

    @Operation(summary = "日历热力图")
    @GetMapping("/heatmap")
    public Result<HeatmapDTO> getHeatmap(Authentication auth) {
        return Result.success(statisticsService.getHeatmap((Long) auth.getPrincipal()));
    }

    @Operation(summary = "每日学习量")
    @GetMapping("/daily-stats")
    public Result<DailyStatsDTO> getDailyStats(Authentication auth,
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(statisticsService.getDailyStats((Long) auth.getPrincipal(), days));
    }

    @Operation(summary = "反应时分布")
    @GetMapping("/reaction-time")
    public Result<ReactionTimeDTO> getReactionTime(Authentication auth,
            @RequestParam(defaultValue = "30") int days) {
        return Result.success(statisticsService.getReactionTime((Long) auth.getPrincipal(), days));
    }

    @Operation(summary = "薄弱词云")
    @GetMapping("/weak-words")
    public Result<List<WeakWordDTO>> getWeakWords(Authentication auth) {
        return Result.success(statisticsService.getWeakWords((Long) auth.getPrincipal()));
    }
}
