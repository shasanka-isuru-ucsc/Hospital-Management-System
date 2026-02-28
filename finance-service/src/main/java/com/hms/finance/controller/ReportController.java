package com.hms.finance.controller;

import com.hms.finance.dto.ApiResponse;
import com.hms.finance.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ─── GET /reports/summary ─────────────────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestParam(defaultValue = "this_month") String period) {

        Map<String, Object> data = reportService.getSummary(period);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ─── GET /reports/earnings ────────────────────────────────────────────────────

    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEarnings(
            @RequestParam(required = false) Integer year) {

        int targetYear = year != null ? year : Year.now().getValue();
        Map<String, Object> data = reportService.getEarningsByYear(targetYear);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ─── GET /reports/departments ─────────────────────────────────────────────────

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDepartments(
            @RequestParam(defaultValue = "this_month") String period) {

        List<Map<String, Object>> data = reportService.getDepartmentStats(period);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
