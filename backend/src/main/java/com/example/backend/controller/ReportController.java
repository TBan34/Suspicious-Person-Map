package com.example.backend.controller;

import com.example.backend.entity.ReportEntity;
import com.example.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Report Controller エンドポイント
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportRepository reportRepository;

    @GetMapping
    public List<ReportEntity> getReports() {
        return reportRepository.findAll();
    }
}