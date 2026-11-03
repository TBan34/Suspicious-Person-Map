package suspiciouspersonmap.service;

import suspiciouspersonmap.common.constant.CommonConst;
import suspiciouspersonmap.common.constant.ReportProcessingStageEnum;
import suspiciouspersonmap.model.ParsedReportMessage;
import suspiciouspersonmap.exception.ReportParseException;
import suspiciouspersonmap.service.parsing.ReportMessageParser;
import suspiciouspersonmap.entity.ReportEntity;
import suspiciouspersonmap.exception.GeocodingException;
import suspiciouspersonmap.exception.ReportProcessingException;
import suspiciouspersonmap.model.GeoPoint;
import suspiciouspersonmap.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ReportRepository reportRepository;
    private final GeocodeService geocodeService;
    private final ReportMessageParser reportMessageParser;

    /**
     * LINE から受信した不審者情報を検証・変換し、座標とともにデータベースへ登録する。
     * レスポンス: なし。
     *
     * @param userId 登録元の LINE ユーザー ID
     * @param text 不審者情報メッセージ
     * @param eventTimestamp LINEイベント時刻（ミリ秒）
     */
    @Transactional
    public void processReportMessage(String userId, String text, Long eventTimestamp) {
        try {
            validateInput(userId, text);
            ParsedReportMessage parsedMessage = reportMessageParser.parse(text, eventTimestamp);
            GeoPoint location = geocode(parsedMessage);
            ReportEntity report = createReport(userId, parsedMessage, location);
            saveReport(report);
        } catch (ReportParseException e) {
            log.error("不審者情報の解析に失敗しました。stage={}, error={}, item={}, reason={}",
                e.getStage().getLogValue(), e.getErrorType(), e.getItem(), e.getReason());
            throw e;
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
                describeMissingValueState(userId),
                exception
            );
        }

        if (StringUtils.isBlank(text)) {
            IllegalArgumentException exception = new IllegalArgumentException("不審者情報は必須です");
            throw new ReportProcessingException(
                ReportProcessingStageEnum.INPUT_VALIDATION,
                "reportMessage",
                describeMissingValueState(text),
                exception
            );
        }
    }

    /**
     * 住所を Geocoding して座標情報を取得し、失敗時の処理情報を例外へ設定する。
     *
     * @param parsedMessage 解析済みの住所項目を含む不審者情報メッセージ
     * @return 住所に対応する座標情報
     */
    private GeoPoint geocode(ParsedReportMessage parsedMessage) {
        try {
            return geocodeService.getLatLng(
                parsedMessage.prefecture(),
                parsedMessage.municipality(),
                parsedMessage.district(),
                parsedMessage.addressDetails()
            );
        } catch (GeocodingException e) {
            throw new ReportProcessingException(
                ReportProcessingStageEnum.GEOCODING,
                "address",
                e.getAddress(),
                e
            );
        } catch (Exception e) {
            throw new ReportProcessingException(
                ReportProcessingStageEnum.GEOCODING,
                "address",
                "not_available",
                e
            );
        }
    }

    /**
     * 変換済みの不審者情報と座標から、データベース登録用 Entity を生成する。
     *
     * @param userId 登録元の LINE ユーザー ID
     * @param parsedMessage 解析済みの不審者情報メッセージ
     * @param location 発生場所の座標情報
     * @return データベース登録用の不審者情報 Entity
     */
    private ReportEntity createReport(
            String userId,
            ParsedReportMessage parsedMessage,
            GeoPoint location) {
        try {
            ReportEntity report = new ReportEntity();
            report.setUserId(userId);

            // タグ設定（最大3個）
            int tagCounter = CommonConst.COUNT.ZERO;
            for (String tag : parsedMessage.tags()) {
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

            // Entity設定
            report.setOccurDate(parsedMessage.occurDate());
            report.setPrefecture(parsedMessage.prefecture());
            report.setMunicipality(parsedMessage.municipality());
            report.setDistrict(parsedMessage.district());
            report.setAddressDetails(parsedMessage.addressDetails());
            report.setLatitude(location.getLatitude());
            report.setLongitude(location.getLongitude());
            report.setSummary(parsedMessage.summary());
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
     * 必須文字列の欠落状態を、内容を露出せず null または blank へ変換する。
     *
     * @param value 欠落状態を判定する文字列
     * @return nullの場合はnull、それ以外の空文字・空白のみの場合はblank
     */
    private String describeMissingValueState(String value) {
        return value == null ? "null" : "blank";
    }

}
