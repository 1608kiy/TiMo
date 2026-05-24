package com.timo.words.modules.calendar.controller;

import com.timo.words.common.Result;
import com.timo.words.modules.calendar.service.CalendarService;
import com.timo.words.modules.calendar.service.CalendarService.MonthlyDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "日历模块")
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @Operation(summary = "月度学习数据")
    @GetMapping("/monthly")
    public Result<MonthlyDTO> getMonthly(
            @RequestParam int year,
            @RequestParam int month,
            Authentication auth) {
        if (month < 1 || month > 12) {
            return Result.error("月份必须为1-12");
        }
        if (year < 2020 || year > 2100) {
            return Result.error("年份不合法");
        }
        return Result.success(calendarService.getMonthly((Long) auth.getPrincipal(), year, month));
    }
}
