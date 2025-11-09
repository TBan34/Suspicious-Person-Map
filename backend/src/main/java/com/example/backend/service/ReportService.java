package com.example.backend.service;

import com.example.backend.entity.ReportEntity;
import com.example.backend.model.GeoPoint;
import com.example.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final GeocodeService geocodeService;

    @Transactional
    public void processReportMessage(String userId, String text) {
        if (StringUtils.isBlank(userId)) {
            log.warn("Received message with empty userId");
            throw new IllegalArgumentException("UserId cannot be empty");
        }
        
        if (StringUtils.isBlank(text)) {
            log.warn("Received empty message from userId: {}", userId);
            throw new IllegalArgumentException("Message text cannot be empty");
        }

        log.info("Processing report message from userId: {}", userId);
        
        // 簡易パース例
        String tag = extractLine(text, "タグ:");
        String prefecture = extractLine(text, "都道府県:");
        String municipality = extractLine(text, "市区町村:");
        String district = extractLine(text, "丁目:");
        String addressDetails = extractLine(text, "番地以降:");
        String summary = extractLine(text, "不審者情報:");

        // 住所→緯度経度に変換
        // NOTE: 一般ユーザーが不審者情報を登録できるようにする際は、正確に住所登録できるよう追加考慮が必要。都道府県+番地のようなあり得ない住所は弾きたい。
        String address = buildAddress(prefecture, municipality, district, addressDetails);
        
        if (address.trim().isEmpty()) {
            log.warn("Empty address provided, cannot geocode. userId: {}", userId);
            throw new IllegalArgumentException("Address is required. Please provide at least prefecture or municipality.");
        }
        
        GeoPoint location = geocodeService.getLatLng(address);

        // DB保存
        ReportEntity report = new ReportEntity();
        report.setUserId(userId);
        report.setTag(tag);
        report.setPrefecture(prefecture);
        report.setMunicipality(municipality);
        report.setDistrict(district);
        report.setAddressDetails(addressDetails);
        report.setLatitude(location.getLatitude());
        report.setLongitude(location.getLongitude());
        report.setSummary(summary);
        // createdは@CreatedDateにより自動設定される
        
        ReportEntity saved = reportRepository.save(report);
        log.info("Successfully saved report with id: {} for userId: {}", saved.getId(), userId);
    }

    private String buildAddress(String prefecture, String municipality, String district, String addressDetails) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(prefecture)) {
            sb.append(prefecture);
        }
        if (StringUtils.isNotBlank(municipality)) {
            if (sb.length() > 0) {
                sb.append(municipality);
            }
        }
        if (StringUtils.isNotBlank(district)) {
            if (sb.length() > 0) {
                sb.append(district);
            };
        }
        if (StringUtils.isNotBlank(addressDetails)) {
            if (sb.length() > 0) {
                sb.append(addressDetails);
            }
        }
        return sb.toString();
    }

    private String extractLine(String text, String prefix) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.replace(prefix, "").trim();
            }
        }
        return "";
    }
}
