package com.timo.words.modules.admin.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.admin.entity.WordImportBatch;
import com.timo.words.modules.admin.service.AdminWordService;
import com.timo.words.modules.word.entity.Word;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "词库管理")
@RestController
@RequestMapping("/api/admin/words")
@RequiredArgsConstructor
public class AdminWordController {

    private final AdminWordService adminWordService;

    @Operation(summary = "词库列表")
    @GetMapping
    public Result<Page<Word>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String examType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminWordService.listWords(keyword, examType, page, size));
    }

    @Operation(summary = "新增单词")
    @PostMapping
    public Result<Word> create(@RequestBody Word word, Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        return Result.success(adminWordService.createWord(word, operatorId, request));
    }

    @Operation(summary = "编辑单词")
    @PutMapping("/{id}")
    public Result<Word> update(@PathVariable Long id, @RequestBody Word word, Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        return Result.success(adminWordService.updateWord(id, word, operatorId, request));
    }

    @Operation(summary = "删除单词")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        adminWordService.deleteWord(id, operatorId, request);
        return Result.success(null);
    }

    @Operation(summary = "批量导入")
    @PostMapping("/import")
    public Result<WordImportBatch> importWords(@RequestParam("file") MultipartFile file,
                                               Authentication auth, HttpServletRequest request) throws IOException {
        Long operatorId = (Long) auth.getPrincipal();
        return Result.success(adminWordService.importWords(file, operatorId, request));
    }

    @Operation(summary = "按考试类型批量删除")
    @DeleteMapping("/by-exam-type")
    public Result<Map<String, Object>> deleteByExamTypes(@RequestBody List<String> examTypes,
                                                         Authentication auth, HttpServletRequest request) {
        Long operatorId = (Long) auth.getPrincipal();
        int count = adminWordService.deleteByExamTypes(examTypes, operatorId, request);
        return Result.success(Map.of("deletedCount", count));
    }
}
