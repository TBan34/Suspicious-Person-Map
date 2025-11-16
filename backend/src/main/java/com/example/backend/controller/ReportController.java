package com.example.backend.controller;

import com.example.backend.dto.ReportDto;
import com.example.backend.entity.ReportEntity;
import com.example.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Report Controller エンドポイント
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportRepository reportRepository;

    /*
     * 不審者情報を取得する。
     * return: 不審者情報Dto
     */
    @GetMapping
    public List<ReportDto> getReports() {
        return reportRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /*
     * Entity→Dto変換
     * reportEntity: 不審者情報Entity
     * return: 不審者情報Dto
     */
    private ReportDto convertToDto(ReportEntity entity) {
        return new ReportDto(
                entity.getId(),
                entity.getUserId(),
                entity.getTag(),
                entity.getPrefecture(),
                entity.getMunicipality(),
                entity.getDistrict(),
                entity.getAddressDetails(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getSummary(),
                entity.getCreated()
        );
    }
}