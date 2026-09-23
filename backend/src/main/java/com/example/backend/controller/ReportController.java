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

    /**
     * 保存されている不審者情報を画面表示用 DTO として取得する。
     *
     * @return 不審者情報 DTO の一覧
     */
    @GetMapping
    public List<ReportDto> getReports() {
        return reportRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 不審者情報 Entity を画面表示用 DTO に変換する。
     *
     * @param entity 変換対象の不審者情報 Entity
     * @return 画面表示用の不審者情報 DTO
     */
    private ReportDto convertToDto(ReportEntity entity) {
        return new ReportDto(
                entity.getId(),
                entity.getTag1(),
                entity.getTag2(),
                entity.getTag3(),
                entity.getOccurDate(),
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
