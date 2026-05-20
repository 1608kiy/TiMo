package com.timo.words.modules.admin.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.admin.entity.AdminOperationLog;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminOperationLogRepository logRepository;

    @Operation(summary = "操作日志列表")
    @GetMapping
    public Result<Page<AdminOperationLog>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminOperationLog> result;
        PageRequest pageRequest = PageRequest.of(page, size);

        boolean hasType = type != null && !type.isBlank();
        boolean hasDate = startDate != null && endDate != null;

        if (hasType && hasDate) {
            result = logRepository.findByOperationTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                    type, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageRequest);
        } else if (hasType) {
            result = logRepository.findByOperationTypeOrderByCreatedAtDesc(type, pageRequest);
        } else if (hasDate) {
            result = logRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                    startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageRequest);
        } else {
            result = logRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        }
        return Result.success(result);
    }
}
