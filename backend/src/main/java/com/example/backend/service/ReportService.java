package com.example.backend.service;

import com.example.backend.common.constant.CommonConst;
import com.example.backend.common.constant.ReportProcessingStageEnum;
import com.example.backend.common.util.DateUtils;
import com.example.backend.entity.ReportEntity;
import com.example.backend.exception.ReportProcessingException;
import com.example.backend.model.GeoPoint;
import com.example.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;

/**
 * 不審者情報周りのサービスロジック
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private record ParsedReportData(
        List<String> tags,
        LocalDateTime occurDate,
        String prefecture,
        String municipality,
        String district,
        String addressDetails,
        String geocodingAddress,
        String summary
    ) {}

    // 不審者情報の各項目
    private static class REPORT_ITEMS {
        private static final String TAG = "タグ:";
        private static final String OCCUR_DATE = "日時:";
        private static final String PREFECTURE = "都道府県:";
        private static final String MUNICIPALITY = "市区町村:";
        private static final String DISTRICT = "丁目:";
        private static final String ADDRESS_DETAILS = "番地以降:";
        private static final String SUMMARY = "概要:";
    }

    private final ReportRepository reportRepository;
    private final GeocodeService geocodeService;

    /**
     * LINE から受信した不審者情報を検証・変換し、座標とともにデータベースへ登録する。
     * レスポンス: なし。
     *
     * @param userId 登録元の LINE ユーザー ID
     * @param text 不審者情報メッセージ
     */
    @Transactional
    public void processReportMessage(String userId, String text) {
        try {
            validateInput(userId, text);
            ParsedReportData reportData = transformReportMessage(text);
            GeoPoint location = geocode(reportData.geocodingAddress());
            ReportEntity report = createReport(userId, reportData, location);
            saveReport(report);
        } catch (ReportProcessingException e) {
            log.error("不審者情報の処理に失敗しました。stage={}, item={}, value={}",
                e.getStage().getLogValue(), e.getItem(), e.getValue(), e);
            throw e;
        }
    }

    /**
     * 不審者情報の登録に必要な入力値が指定されていることを検証する。
     * レスポンス: なし。
     *
     * @param userId 登録元の LINE ユーザー ID
     * @param text 不審者情報メッセージ
     */
    private void validateInput(String userId, String text) {
        if (StringUtils.isBlank(userId)) {
            IllegalArgumentException exception = new IllegalArgumentException("ユーザーIDは必須です");
            throw new ReportProcessingException(
                ReportProcessingStageEnum.INPUT_VALIDATION,
                "userId",
                describeValueState(userId),
                exception
            );
        }

        if (StringUtils.isBlank(text)) {
            IllegalArgumentException exception = new IllegalArgumentException("不審者情報は必須です");
            throw new ReportProcessingException(
                ReportProcessingStageEnum.INPUT_VALIDATION,
                "reportMessage",
                describeValueState(text),
                exception
            );
        }
    }

    /**
     * 不審者情報メッセージから登録項目を抽出し、登録処理で扱う形式へ変換する。
     *
     * @param text 不審者情報メッセージ
     * @return 抽出・変換・正規化済みの不審者情報
     */
    private ParsedReportData transformReportMessage(String text) {
        try {
            List<String> tags = extractLineToMultiple(text, REPORT_ITEMS.TAG);
            String occurDateText = extractLineToSingle(text, REPORT_ITEMS.OCCUR_DATE);
            String prefecture = extractLineToSingle(text, REPORT_ITEMS.PREFECTURE);
            String municipality = extractLineToSingle(text, REPORT_ITEMS.MUNICIPALITY);
            String district = extractLineToSingle(text, REPORT_ITEMS.DISTRICT);
            String addressDetails = extractLineToSingle(text, REPORT_ITEMS.ADDRESS_DETAILS);
            String summary = extractLineToSingle(text, REPORT_ITEMS.SUMMARY);

            LocalDateTime occurDate = parseOccurDate(occurDateText);
            addressCheck(prefecture, municipality, district);

            String address = buildAddress(prefecture, municipality, district, addressDetails);
            String geocodingAddress = normalizeAddress(address);

            return new ParsedReportData(
                tags,
                occurDate,
                prefecture,
                municipality,
                district,
                addressDetails,
                geocodingAddress,
                summary
            );
        } catch (ReportProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportProcessingException(
                ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION,
                "reportMessage",
                "not_logged",
                e
            );
        }
    }

    /**
     * 発生日時の文字列を LocalDateTime に変換し、失敗時の項目情報を例外へ設定する。
     *
     * @param occurDate 発生日時の文字列
     * @return 変換後の発生日時
     */
    private LocalDateTime parseOccurDate(String occurDate) {
        try {
            return DateUtils.parseToLocalDateTime(occurDate);
        } catch (Exception e) {
            throw new ReportProcessingException(
                ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION,
                "occurDate",
                occurDate,
                e
            );
        }
    }

    /**
     * 住所を Geocoding して座標情報を取得し、失敗時の処理情報を例外へ設定する。
     *
     * @param address Geocoding 対象の住所
     * @return 住所に対応する座標情報
     */
    private GeoPoint geocode(String address) {
        try {
            return geocodeService.getLatLng(address);
        } catch (Exception e) {
            throw new ReportProcessingException(
                ReportProcessingStageEnum.GEOCODING,
                "address",
                address,
                e
            );
        }
    }

    /**
     * 変換済みの不審者情報と座標から、データベース登録用 Entity を生成する。
     *
     * @param userId 登録元の LINE ユーザー ID
     * @param reportData 変換済みの不審者情報
     * @param location 発生場所の座標情報
     * @return データベース登録用の不審者情報 Entity
     */
    private ReportEntity createReport(String userId, ParsedReportData reportData, GeoPoint location) {
        try {
            ReportEntity report = new ReportEntity();
            report.setUserId(userId);

            int tagCounter = CommonConst.COUNT.ZERO;
            for (String tag : reportData.tags()) {
                if (tagCounter == CommonConst.COUNT.ZERO) {
                    report.setTag1(tag);
                    tagCounter++;
                } else if (tagCounter == CommonConst.COUNT.ONE) {
                    report.setTag2(tag);
                    tagCounter++;
                } else if (tagCounter == CommonConst.COUNT.TWO) {
                    report.setTag3(tag);
                }
            }

            report.setOccurDate(reportData.occurDate());
            report.setPrefecture(reportData.prefecture());
            report.setMunicipality(reportData.municipality());
            report.setDistrict(reportData.district());
            report.setAddressDetails(reportData.addressDetails());
            report.setLatitude(location.getLatitude());
            report.setLongitude(location.getLongitude());
            report.setSummary(reportData.summary());
            // createdは@CreatedDateにより自動設定される

            return report;
        } catch (Exception e) {
            throw new ReportProcessingException(
                ReportProcessingStageEnum.REPORT_CREATION,
                "report",
                "not_created",
                e
            );
        }
    }

    /**
     * 不審者情報 Entity をデータベースへ即時保存する。
     * レスポンス: なし。
     *
     * @param report 保存対象の不審者情報 Entity
     */
    private void saveReport(ReportEntity report) {
        try {
            // DB例外をこの処理段階で捕捉するため、即時にflushする。
            ReportEntity saved = reportRepository.saveAndFlush(report);
            log.info("不審者情報の登録に成功しました。reportId={}", saved.getId());
        } catch (Exception e) {
            String reportValue = "occurDate=" + report.getOccurDate()
                + ", prefecture=" + report.getPrefecture()
                + ", municipality=" + report.getMunicipality();
            throw new ReportProcessingException(
                ReportProcessingStageEnum.DATABASE_SAVE,
                "report",
                reportValue,
                e
            );
        }
    }

    /**
     * 文字列の内容を露出せず、null・空白・値ありの状態へ変換する。
     *
     * @param value 状態を判定する文字列
     * @return null、blank、present のいずれかの状態値
     */
    private String describeValueState(String value) {
        if (value == null) {
            return "null";
        }

        if (StringUtils.isBlank(value)) {
            return "blank";
        }

        return "present";
    }

    /**
     * 不審者情報メッセージから、指定された項目の単一値を抽出する。
     *
     * @param text 不審者情報メッセージ
     * @param item 抽出対象の項目名
     * @return 抽出した値。対象項目がない場合は null
     */
    private String extractLineToSingle(String text, String item) {

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

    /**
     * 不審者情報メッセージから、指定された項目の複数値を抽出する。
     *
     * @param text 不審者情報メッセージ
     * @param item 抽出対象の項目名
     * @return 抽出した値の一覧。対象項目がない場合は null
     */
    private List<String> extractLineToMultiple(String text, String item) {

        // 不審者情報を項目単位で抽出
        String[] lines = text.split("[\r\n]+"); // OSごとの改行コードを考慮し分割
        List<String> lineList = Arrays.asList(lines);
        List<String> valuesWithItem = null;
        for (String line : lineList) {
            String singleLine = line.trim();
            if (singleLine.startsWith(item)) {
                // 「タグ：不審な声かけ、撮影行為」 → 「不審な声かけ、撮影行為」のように値抽出
                // 空行、カンマ、読点を考慮
                valuesWithItem = Arrays.asList(line.split("\\s*[,、]\\s*"));
                List<String> values = new ArrayList<>();
                for (String value : valuesWithItem) {
                    if (value.startsWith(item)) {
                        value = value.substring(item.length());
                    }
                    value.replace(":", "")
                         .replace("：", "");
                    values.add(value);
                }
                return values;
            }
        }
        return null;
    }

    /**
     * Geocoding に必要な住所項目が入力されていることを検証する。
     * レスポンス: なし。
     *
     * @param prefecture 都道府県
     * @param municipality 市区町村
     * @param district 丁目
     */
    // NOTE: 文字数のバリデーションは要検討
    private void addressCheck(String prefecture, String municipality, String district) {
        validateRequiredAddressItem("prefecture", prefecture, "都道府県は必須です");
        validateRequiredAddressItem("municipality", municipality, "市区町村は必須です");
        validateRequiredAddressItem("district", district, "丁目は必須です");
    }

    /**
     * 必須住所項目が入力されていることを検証し、未入力時の項目情報を例外へ設定する。
     * レスポンス: なし。
     *
     * @param item ログへ記録する住所項目名
     * @param value 検証対象の住所項目値
     * @param errorMessage 未入力時に設定するエラーメッセージ
     */
    private void validateRequiredAddressItem(String item, String value, String errorMessage) {
        if (StringUtils.isBlank(value)) {
            IllegalArgumentException exception = new IllegalArgumentException(errorMessage);
            throw new ReportProcessingException(
                ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION,
                item,
                describeValueState(value),
                exception
            );
        }
    }

    /**
     * 住所の各項目を Geocoding 用の一つの住所文字列へ結合する。
     *
     * @param prefecture 都道府県
     * @param municipality 市区町村
     * @param district 丁目
     * @param addressDetails 番地以降の任意情報
     * @return 結合した住所
     */
    private String buildAddress(String prefecture, String municipality, String district, String addressDetails) {

        // GeocodeService.getLatLng()にて、マップピン留め用の座標情報を取得するために住所情報を結合する。
        StringBuilder sb = new StringBuilder();
        sb.append(prefecture);
        sb.append(municipality);
        sb.append(district);
        
        if (StringUtils.isNotBlank(addressDetails)) {
            sb.append(addressDetails);
        }
        
        return sb.toString();
    }

    /**
     * 住所の空白や文字幅を統一し、Geocoding 用の形式へ正規化する。
     *
     * @param address 正規化対象の住所
     * @return 正規化した住所。入力が null または空文字の場合は null
     */
    private String normalizeAddress(String address) {

        if (StringUtils.isEmpty(address)) {
            return null;
        }

        // 1) 前後の空白・改行除去
        address = address.trim();

        // 2) 全角英数字・記号を半角寄せ（３→3、－→- など）
        address = Normalizer.normalize(address, Normalizer.Form.NFKC);

        // 3) 空白の除去
        address.replace(" ", "");
        address.replace("　", "");

        return address;
    }
}
