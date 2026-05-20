package com.timo.words.modules.word.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.word.dto.WordDTO;
import com.timo.words.modules.word.service.WordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "词库模块")
@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @Operation(summary = "单词列表（分页）")
    @GetMapping
    public Result<Page<WordDTO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String pos,
            @RequestParam(required = false) String familiarity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 100);
        Long userId = (Long) authentication.getPrincipal();
        if (familiarity != null && !familiarity.isBlank()) {
            return Result.success(wordService.listWithFamiliarity(keyword, examType, pos, familiarity, userId, page, size));
        }
        return Result.success(wordService.list(keyword, examType, pos, familiarity, page, size));
    }

    @Operation(summary = "搜索单词")
    @GetMapping("/search")
    public Result<List<WordDTO>> search(@RequestParam String keyword) {
        return Result.success(wordService.search(keyword));
    }

    @Operation(summary = "批量获取单词")
    @GetMapping("/batch")
    public Result<List<WordDTO>> getByIds(@RequestParam List<Long> ids) {
        return Result.success(wordService.getByIds(ids));
    }

    @Operation(summary = "词库总数")
    @GetMapping("/count")
    public Result<Long> count(@RequestParam(required = false) String examType) {
        if (examType != null && !examType.isBlank()) {
            return Result.success(wordService.countByExamType(examType));
        }
        return Result.success(wordService.count());
    }

    @Operation(summary = "各考试类型词数统计")
    @GetMapping("/count-by-type")
    public Result<List<Object[]>> countByType() {
        return Result.success(wordService.countByExamTypeGroup());
    }

    @Operation(summary = "获取单词FSRS状态")
    @GetMapping("/{id}/fsrs-state")
    public Result<WordDTO> getWordFsrsState(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(wordService.getWordFsrsState(id, userId));
    }

    @Operation(summary = "单词详情")
    @GetMapping("/{id}")
    public Result<WordDTO> detail(@PathVariable Long id) {
        return Result.success(wordService.detail(id));
    }
}
