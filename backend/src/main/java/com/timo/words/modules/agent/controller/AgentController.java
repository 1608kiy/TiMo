package com.timo.words.modules.agent.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.agent.service.AgentService;
import com.timo.words.modules.agent.service.AgentService.ChatRequest;
import com.timo.words.modules.agent.service.AgentService.ChatResponse;
import com.timo.words.modules.agent.service.AgentService.LogQuizRequest;
import com.timo.words.modules.agent.service.AgentService.PassageRequest;
import com.timo.words.modules.agent.service.AgentService.PassageResponse;
import com.timo.words.modules.agent.service.AgentService.RecommendResponse;
import com.timo.words.modules.agent.service.AgentService.WeeklyReportResponse;
import com.timo.words.modules.agent.service.AgentService.ProgressAlertResponse;
import com.timo.words.modules.agent.service.AgentService.AnalyzeWordRequest;
import com.timo.words.modules.agent.service.AgentService.AnalyzeWordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI Agent 模块")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "发送消息给TiMo")
    @PostMapping("/chat/send")
    public Result<ChatResponse> sendMessage(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(agentService.sendChat(request));
    }

    @Operation(summary = "获取聊天历史")
    @GetMapping("/chat/history")
    public Result<List<Map<String, String>>> loadHistory(
            @RequestParam Long sessionId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(agentService.loadHistory(userId, sessionId));
    }

    @Operation(summary = "获取今日学习推荐")
    @GetMapping("/recommend")
    public Result<RecommendResponse> getRecommendation(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(agentService.getRecommendation(userId));
    }

    @Operation(summary = "获取周报")
    @GetMapping("/weekly-report")
    public Result<WeeklyReportResponse> getWeeklyReport(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(agentService.getWeeklyReport(userId));
    }

    @Operation(summary = "生成嵌入单词的短文")
    @PostMapping("/generate-passage")
    public Result<PassageResponse> generatePassage(
            @Valid @RequestBody PassageRequest request,
            Authentication authentication) {
        return Result.success(agentService.generatePassage(request));
    }

    @Operation(summary = "根据单词生成选择题")
    @PostMapping("/generate-questions")
    public Result<Map<String, Object>> generateQuestions(
            @RequestBody Map<String, List<String>> body,
            Authentication authentication) {
        List<String> words = body.getOrDefault("words", List.of());
        return Result.success(agentService.generateQuestions(words));
    }

    @Operation(summary = "获取顽固词分析")
    @GetMapping("/stubborn-words")
    public Result<Map<String, Object>> getStubbornWords(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(agentService.getStubbornWordsAnalysis(userId));
    }

    @Operation(summary = "记录对话中的练习结果")
    @PostMapping("/log-quiz")
    public Result<Void> logConversationQuiz(
            @Valid @RequestBody LogQuizRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        agentService.logConversationQuiz(userId, request);
        return Result.success(null);
    }

    @Operation(summary = "检查备考进度是否落后")
    @GetMapping("/progress-alert")
    public Result<ProgressAlertResponse> checkProgressAlert(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(agentService.checkProgressAlert(userId));
    }

    @Operation(summary = "深度分析单词")
    @PostMapping("/analyze-word")
    public Result<AnalyzeWordResponse> analyzeWord(
            @Valid @RequestBody AnalyzeWordRequest request,
            Authentication authentication) {
        return Result.success(agentService.analyzeWord(request.getWord()));
    }
}
