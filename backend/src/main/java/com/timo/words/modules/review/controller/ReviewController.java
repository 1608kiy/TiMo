package com.timo.words.modules.review.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.review.service.ReviewService;
import com.timo.words.modules.review.service.ReviewService.NearForgottenWordDTO;
import com.timo.words.modules.review.service.ReviewService.ReviewQueueResponse;
import com.timo.words.modules.review.service.ReviewService.ReviewResultRequest;
import com.timo.words.modules.study.service.StudyService.SubmitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "复习模块")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "获取复习队列")
    @GetMapping("/queue")
    public Result<ReviewQueueResponse> getQueue(
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(reviewService.getReviewQueue(userId, limit));
    }

    @Operation(summary = "提交复习结果")
    @PostMapping("/result")
    public Result<SubmitResponse> submitResult(
            @RequestBody ReviewResultRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        return Result.success(reviewService.submitReview(request));
    }

    @Operation(summary = "获取即将遗忘的单词")
    @GetMapping("/near-forgotten")
    public Result<List<NearForgottenWordDTO>> getNearForgotten(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(reviewService.getNearForgotten(userId));
    }
}
