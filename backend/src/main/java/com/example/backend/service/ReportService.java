package com.example.backend.service;

import com.example.backend.entity.ReportEntity;
import com.example.backend.model.GeoPoint;
import com.example.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 不審者情報周りのサービスロジック
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    // 不審者情報の各項目
    private static class REPORT_ITEMS {
        private static final String TAG = "タグ:";
        private static final String PREFECTURE = "都道府県:";
        private static final String MUNICIPALITY = "市区町村:";
        private static final String DISTRICT = "丁目:";
        private static final String ADDRESS_DETAILS = "番地以降:";
        private static final String SUMMARY = "概要:";
    }

    private final ReportRepository reportRepository;
    private final GeocodeService geocodeService;

    /*
     * 不審者情報を登録する。
     * userId: ユーザーID
     * text: 不審者情報メッセージ
     */
    @Transactional
    public void processReportMessage(String userId, String text) {

        // パラメータチェック
        paramCheck(userId, text);
        
        // 不審者情報の抽出
        String tag = extractLine(text, REPORT_ITEMS.TAG);
        String prefecture = extractLine(text, REPORT_ITEMS.PREFECTURE);
        String municipality = extractLine(text, REPORT_ITEMS.MUNICIPALITY);
        String district = extractLine(text, REPORT_ITEMS.DISTRICT);
        String addressDetails = extractLine(text, REPORT_ITEMS.ADDRESS_DETAILS);
        String summary = extractLine(text, REPORT_ITEMS.SUMMARY);

        // 住所情報チェック　※番地以降の情報は任意
        addressCheck(prefecture, municipality, district);
        // 住所情報結合
        String address = buildAddress(prefecture, municipality, district, addressDetails);
        // 住所情報の正規化
        address = normalizeAddress(address);
        // 座標情報取得（緯度経度）
        GeoPoint location = geocodeService.getLatLng(address);

        // Entity作成
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
        
        // 登録処理
        ReportEntity saved = reportRepository.save(report);
        log.info("Successfully saved report with id: {} for userId: {}", saved.getId(), userId);
    }

    /*
     * パラメータチェック
     * userId: ユーザーID
     * text: 不審者情報メッセージ
     */
    private void paramCheck(String userId, String text) {

        // ユーザーIDがnullまたは空の場合
        if (StringUtils.isBlank(userId)) {
            log.warn("Received message with empty userId");
            throw new IllegalArgumentException("UserId cannot be empty");
        }

        // 不審者情報メッセージがnullまたは空の場合
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

        // 不審者情報を項目単位で抽出
        String[] lines = text.split("[\r\n]+"); // OSごとの改行コードを考慮し分割
        List<String> lineList = Arrays.asList(lines);
        for (String line : lineList) {
            String singleLine = line.trim();
            if (singleLine.startsWith(item)) {
                // 「都道府県：福岡県」 → 「福岡県」のように値抽出
                String value = singleLine.substring(item.length());
                return value.replace(":", "")
                            .replace("：", "")
                            .trim();
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

        // 必須チェック：　都道府県
        if (StringUtils.isBlank(prefecture)) {
            throw new IllegalArgumentException("prefecture cannot be empty");
        }

        // 必須チェック：　市区町村
        if (StringUtils.isBlank(municipality)) {
            throw new IllegalArgumentException("municipality cannot be empty");
        }

        // 必須チェック：　丁目
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

        // 後続のGeocodeService.getLatLng()にて、マップピン留め用の座標情報を取得するため、住所情報を結合する。
        StringBuilder sb = new StringBuilder();
        sb.append(prefecture);
        sb.append(municipality);
        sb.append(district);
        
        if (StringUtils.isNotBlank(addressDetails)) {
            sb.append(" ");
            sb.append(addressDetails);
        }
        
        return sb.toString();
    }

        /*
     * 住所情報の正規化
     * address: 住所情報
     * return: 住所情報
     */
    private String normalizeAddress(String address) {

        return address
            .replace(" ", "")
            .replace("　", "")
            .replace("丁目", "-")
            .replace("丁", "-")
            .replace("番地", "-")
            .replace("番", "-")
            .replace("号", "");
    }
}
