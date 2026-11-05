package suspiciouspersonmap.service;

import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.PropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import suspiciouspersonmap.common.constant.CommonConst;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.common.constant.ReportProcessingStageEnum;
import suspiciouspersonmap.entity.ReportEntity;
import suspiciouspersonmap.exception.GeocodingException;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.model.GeoPoint;
import suspiciouspersonmap.model.ParsedReportMessage;
import suspiciouspersonmap.repository.ReportRepository;
import suspiciouspersonmap.service.parsing.ReportMessageParser;

/**
 * 不審者情報周りのサービスロジック
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private static final String DATABASE_ITEM = "database";
    private static final String UNKNOWN_EXCEPTION_TYPE = "unknown";
    private static final String UNKNOWN_ROOT_CAUSE_TYPE = "unknown";
    private static final String UNKNOWN_SQL_STATE = "unknown";
    private static final String UNKNOWN_CONSTRAINT_NAME = "unknown";

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
        } catch (ReportException e) {
            logProcessingFailure(e);
            throw e;
        }
    }

    /**
     * 不審者情報の処理エラーを、例外の種類と処理段階に応じてログへ記録する。
     * 解析例外とDB保存例外は、入力値の漏えいを避けるため専用のログ形式を使用する。
     * レスポンス: なし。
     *
     * @param exception 不審者情報の処理中に発生した例外
     */
    private void logProcessingFailure(ReportException exception) {
        switch (exception.getStage()) {
            case REPORT_MESSAGE_TRANSFORMATION ->
                log.error("不審者情報の解析に失敗しました。stage={}, error={}, item={}, reason={}",
                    exception.getStage().getLogValue(), exception.getErrorType(),
                    exception.getItem(), exception.getDetail());
            case DATABASE_SAVE -> logDatabaseSaveFailure(exception.getCause());
            default -> log.error("不審者情報の処理に失敗しました。stage={}, item={}, value={}",
                exception.getStage().getLogValue(), exception.getItem(), exception.getDetail(), exception);
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
            throw new ReportException(
                ReportErrorEnum.INVALID_INPUT,
                "userId",
                describeMissingValueState(userId),
                exception
            );
        }

        if (StringUtils.isBlank(text)) {
            IllegalArgumentException exception = new IllegalArgumentException("不審者情報は必須です");
            throw new ReportException(
                ReportErrorEnum.INVALID_INPUT,
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
            throw new ReportException(
                ReportErrorEnum.GEOCODING_FAILED,
                "address",
                e.getAddress(),
                e
            );
        } catch (Exception e) {
            throw new ReportException(
                ReportErrorEnum.GEOCODING_FAILED,
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
            throw new ReportException(
                ReportErrorEnum.REPORT_CREATION_FAILED,
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
            throw new ReportException(
                ReportErrorEnum.DATABASE_SAVE_FAILED,
                DATABASE_ITEM,
                null,
                e
            );
        }
    }

    /**
     * DB保存例外から、登録値を含まない診断情報だけを取得してログへ記録する。
     * 例外メッセージとスタックトレースは失敗したSQLや登録値を含み得るため出力しない。
     * レスポンス: なし。
     *
     * @param cause DB保存時に発生した原因例外。取得できなかった場合はnull
     */
    private void logDatabaseSaveFailure(Throwable cause) {
        String exceptionType = cause == null
            ? UNKNOWN_EXCEPTION_TYPE
            : cause.getClass().getSimpleName();

        log.error("不審者情報のDB保存に失敗しました。stage={}, item={}, exceptionType={}, "
                + "rootCauseType={}, sqlState={}, constraint={}",
            ReportProcessingStageEnum.DATABASE_SAVE.getLogValue(),
            findDatabaseErrorItem(cause),
            exceptionType,
            findRootCauseType(cause),
            findSqlState(cause),
            findConstraintName(cause));
    }

    /**
     * DB保存例外の原因チェーンから、Hibernateが特定したEntityのプロパティ名を探す。
     *
     * @param cause DB保存時に発生した原因例外
     * @return プロパティ名。特定できない場合はdatabase
     */
    private String findDatabaseErrorItem(Throwable cause) {
        for (Throwable currentCause = cause;
                currentCause != null;
                currentCause = currentCause.getCause()) {
            if (currentCause instanceof PropertyValueException propertyValueException
                    && StringUtils.isNotBlank(propertyValueException.getPropertyName())) {
                return propertyValueException.getPropertyName();
            }
        }
        return DATABASE_ITEM;
    }

    /**
     * DB保存例外の原因チェーンから、JDBC例外のSQLStateを探す。
     *
     * @param cause DB保存時に発生した原因例外
     * @return SQLState。取得できない場合はunknown
     */
    private String findSqlState(Throwable cause) {
        for (Throwable currentCause = cause;
                currentCause != null;
                currentCause = currentCause.getCause()) {
            if (currentCause instanceof SQLException sqlException
                    && StringUtils.isNotBlank(sqlException.getSQLState())) {
                return sqlException.getSQLState();
            }
        }
        return UNKNOWN_SQL_STATE;
    }

    /**
     * DB保存例外の原因チェーンから、Hibernateが保持するDB制約名を探す。
     *
     * @param cause DB保存時に発生した原因例外
     * @return DB制約名。取得できない場合はunknown
     */
    private String findConstraintName(Throwable cause) {
        for (Throwable currentCause = cause;
                currentCause != null;
                currentCause = currentCause.getCause()) {
            if (currentCause instanceof ConstraintViolationException constraintViolationException
                    && StringUtils.isNotBlank(constraintViolationException.getConstraintName())) {
                return constraintViolationException.getConstraintName();
            }
        }
        return UNKNOWN_CONSTRAINT_NAME;
    }

    /**
     * DB保存例外の原因チェーンを末端までたどり、最深部の例外種別を取得する。
     *
     * @param cause DB保存時に発生した原因例外
     * @return 最深部の例外のクラス名。取得できない場合はunknown
     */
    private String findRootCauseType(Throwable cause) {
        String rootCauseType = UNKNOWN_ROOT_CAUSE_TYPE;
        for (Throwable currentCause = cause;
                currentCause != null;
                currentCause = currentCause.getCause()) {
            rootCauseType = currentCause.getClass().getSimpleName();
        }
        return rootCauseType;
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
