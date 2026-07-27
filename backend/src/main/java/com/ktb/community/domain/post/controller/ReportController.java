package com.ktb.community.domain.post.controller;

import com.ktb.community.global.common.ApiResponse;
import com.ktb.community.domain.post.entity.ReportType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<ReportType>>> getReportTypes() {
        return ResponseEntity.ok(
                new ApiResponse<>("get_report_types_success", Arrays.asList(ReportType.values()))
        );
    }
}
