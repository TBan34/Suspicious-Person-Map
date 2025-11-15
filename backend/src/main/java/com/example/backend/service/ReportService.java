package com.example.backend.service;

import com.example.backend.entity.ReportEntity;
import com.example.backend.model.GeoPoint;
import com.example.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LINEから不審者情報メッセージを受信し、DBに保存する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final GeocodeService geocodeService;

    @Transactional
    public void processReportMessage(String userId, String text) {

        // パラメータチェック
        paramCheck(userId, text);
        
        // 不審者情報の抽出
        String tag = extractLine(text, "タグ:");
        String prefecture = extractLine(text, "都道府県:");
        String municipality = extractLine(text, "市区町村:");
        String district = extractLine(text, "丁目:");
        String addressDetails = extractLine(text, "番地以降:");
        String summary = extractLine(text, "概要:");

        // 住所情報チェック　※番地以降の情報は任意
        addressCheck(prefecture, municipality, district);
        // 住所情報結合
        String address = buildAddress(prefecture, municipality, district, addressDetails);
        // 座標情報取得（緯度経度）
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

    /*
     * パラメータチェック
     * userId: ユーザーID
     * text: 不審者情報メッセージ
     */
    private void paramCheck(String userId, String text) {
        if (StringUtils.isBlank(userId)) {
            log.warn("Received message with empty userId");
            throw new IllegalArgumentException("UserId cannot be empty");
        }

        if (StringUtils.isBlank(text)) {
            log.warn("Received empty message from userId: {}", userId);
            throw new IllegalArgumentException("Message text cannot be empty");
        }
    }

    /*
     * 不審者情報の抽出
     * text: 不審者情報メッセージ
     * item: 抽出対象項目
     * return: 抽出結果
     */
    private String extractLine(String text, String item) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        for (String line : text.split("\n")) {
            if (line.startsWith(item)) {
                return line.replace(item, "").trim();
            }
        }
        return null;
    }

    /*
     * 住所情報チェック
     * prefecture: 都道府県
     * municipality: 市区町村
     * district: 丁目
     */
    // NOTE: 文字数のバリデーションは要検討
    private void addressCheck(String prefecture, String municipality, String district) {
        if (StringUtils.isBlank(prefecture)) {
            throw new IllegalArgumentException("prefecture cannot be empty");
        }

        if (StringUtils.isBlank(municipality)) {
            throw new IllegalArgumentException("municipality cannot be empty");
        }

        if (StringUtils.isBlank(district)) {
            throw new IllegalArgumentException("district cannot be empty");
        }
    }

    /*
     * 住所情報結合
     * prefecture: 都道府県
     * municipality: 市区町村
     * district: 丁目
     * addressDetails: 番地以降
     * return: 住所情報
     */
    private String buildAddress(String prefecture, String municipality, String district, String addressDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefecture);
        sb.append(municipality);
        sb.append(district);
        if (StringUtils.isNotBlank(addressDetails)) {
            sb.append(addressDetails);
        }
        return sb.toString();
    }
}
