package com.timo.words.modules.study.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.study.service.StudyService;
import com.timo.words.modules.study.service.StudyService.ContextDeepGroupSubmitRequest;
import com.timo.words.modules.study.service.StudyService.ContextDeepSubmitRequest;
import com.timo.words.modules.study.service.StudyService.QuickMemorySubmitRequest;
import com.timo.words.modules.study.service.StudyService.ReverseRecallCandidate;
import com.timo.words.modules.study.service.StudyService.ReverseRecallSubmitRequest;
import com.timo.words.modules.study.service.StudyService.SubmitResponse;
import com.timo.words.modules.study.service.StudyService.UnifiedReviewSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "学习模块")
@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @Operation(summary = "提交快速记忆")
    @PostMapping("/submit-quick-memory")
    public Result<SubmitResponse> submitQuickMemory(
            @RequestBody QuickMemorySubmitRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(studyService.submitQuickMemory(request));
    }

    @Operation(summary = "提交语境深度学习")
    @PostMapping("/submit-context-deep")
    public Result<SubmitResponse> submitContextDeep(
            @RequestBody ContextDeepSubmitRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(studyService.submitContextDeepWord(request));
    }

    @Operation(summary = "提交统一复习")
    @PostMapping("/submit-review")
    public Result<SubmitResponse> submitReview(
            @RequestBody UnifiedReviewSubmitRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(studyService.submitUnifiedReview(request));
    }

    @Operation(summary = "提交语境深度学习小组结果")
    @PostMapping("/submit-context-deep-group")
    public Result<Void> submitContextDeepGroup(
            @RequestBody ContextDeepGroupSubmitRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        studyService.submitContextDeepGroup(request);
        return Result.success(null);
    }

    @Operation(summary = "提交中→英主动召回")
    @PostMapping("/submit-reverse-recall")
    public Result<SubmitResponse> submitReverseRecall(
            @RequestBody ReverseRecallSubmitRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(studyService.submitReverseRecall(request));
    }

    @Operation(summary = "获取中→英主动召回候选词")
    @GetMapping("/reverse-recall/candidates")
    public Result<List<ReverseRecallCandidate>> getReverseRecallCandidates(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(studyService.getReverseRecallCandidates(userId, limit));
    }
}
