package suspiciouspersonmap.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import suspiciouspersonmap.common.constant.ReportProcessingStageEnum;
import suspiciouspersonmap.entity.ReportEntity;
import suspiciouspersonmap.exception.GeocodingException;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.model.GeoPoint;
import suspiciouspersonmap.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceTest {

    private static final String VALID_REPORT_MESSAGE = "2025年9月8日午後6時10分、"
        + "福岡市中央区天神1丁目1-1付近の路上で、女性が男から「何年生？」と声をかけられました。";

    /**
     * 入力値検証の失敗時に対象項目と値の状態だけが記録され、メッセージ本文が記録されないことを確認する。
     */
    @Test
    void logsInputValidationItemAndValueState() {
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            null,
            ReportParserTestSupport.createParser()
        );
        ListAppender<ILoggingEvent> appender = attachListAppender();

        try {
            assertThatThrownBy(() -> reportService.processReportMessage(null, VALID_REPORT_MESSAGE, 1792897200000L))
                .isInstanceOf(ReportException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=input_validation")
                .contains("item=userId")
                .contains("value=null")
                .doesNotContain(VALID_REPORT_MESSAGE);
        } finally {
            detachListAppender(appender);
        }
    }

    /**
     * 発生日時の変換失敗時に種別と項目が記録され、不正値やLINEユーザーIDが記録されないことを確認する。
     */
    @Test
    void logsReportMessageTransformationWithoutInvalidValue() {
        String invalidOccurDate = "invalid-occur-date";
        String invalidMessage = VALID_REPORT_MESSAGE.replace(
            "2025年9月8日午後6時10分",
            invalidOccurDate
        );
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            null,
            ReportParserTestSupport.createParser()
        );
        ListAppender<ILoggingEvent> appender = attachListAppender();

        try {
            assertThatThrownBy(() -> reportService.processReportMessage("line-user-id", invalidMessage, 1792897200000L))
                .isInstanceOf(ReportException.class);

            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=report_message_transformation")
                .contains("item=occurDate")
                .contains("error=INVALID_DATETIME")
                .doesNotContain(invalidOccurDate)
                .doesNotContain("line-user-id")
                .doesNotContain("何年生");
        } finally {
            detachListAppender(appender);
        }
    }

    /**
     * 住所欠落時に、住所項目と住所不正の種別が記録されることを確認する。
     */
    @Test
    void logsMissingAddressItemAtValidationPoint() {
        String messageWithoutAddress = VALID_REPORT_MESSAGE.replace("福岡市中央区天神1丁目1-1", "");
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            null,
            ReportParserTestSupport.createParser()
        );
        ListAppender<ILoggingEvent> appender = attachListAppender();

        try {
            assertThatThrownBy(() -> reportService.processReportMessage("line-user-id", messageWithoutAddress, 1792897200000L))
                .isInstanceOf(ReportException.class)
                .hasNoCause();

            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=report_message_transformation")
                .contains("item=address")
                .contains("error=INVALID_ADDRESS")
                .doesNotContain("line-user-id")
                .doesNotContain("何年生");
        } finally {
            detachListAppender(appender);
        }
    }

    /**
     * タグを判定できない文章をEntity生成前の解析段階で拒否し、入力値をログへ記録しないことを確認する。
     */
    @Test
    void rejectsUnclassifiableReportBeforeGeocoding() {
        String unclassifiableMessage = VALID_REPORT_MESSAGE.replace(
            "女性が男から「何年生？」と声をかけられました。", "男が歩いていました。");
        GeocodeService geocodeService = new GeocodeService(
                new RestTemplate(),
                new ObjectMapper(),
                new GeocodingAddressCandidateGenerator(new AddressNormalizer())) {
            /**
             * Entity 生成処理まで進めるため、固定の座標情報を返す。
             *
             * @param prefecture 都道府県
             * @param municipality 市区町村
             * @param district 丁目
             * @param addressDetails 番地以降の任意情報
             * @return テスト用の固定座標
             */
            @Override
            public GeoPoint getLatLng(
                    String prefecture,
                    String municipality,
                    String district,
                    String addressDetails) {
                return new GeoPoint(33.5902, 130.4017);
            }
        };
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            geocodeService,
            ReportParserTestSupport.createParser()
        );
        ListAppender<ILoggingEvent> appender = attachListAppender();

        try {
            assertThatThrownBy(() -> reportService.processReportMessage("line-user-id", unclassifiableMessage, 1792897200000L))
                .isInstanceOf(ReportException.class)
                .hasNoCause();

            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=report_message_transformation")
                .contains("error=UNCLASSIFIABLE_TAG")
                .doesNotContain("line-user-id")
                .doesNotContain("男が歩いていました");
            assertThat(errorEvents.get(0).getThrowableProxy()).isNull();
        } finally {
            detachListAppender(appender);
        }
    }

    /**
     * Geocoding 失敗時に例外が処理情報を保持し、その情報と原因例外が一度だけ記録されることを確認する。
     */
    @Test
    void logsGeocodingStageAndAddressOnce() {
        GeocodingException geocodingException = new GeocodingException(
            "Geocoding失敗",
            "福岡県福岡市中央1丁目1-1"
        );
        GeocodeService geocodeService = new GeocodeService(
                new RestTemplate(),
                new ObjectMapper(),
                new GeocodingAddressCandidateGenerator(new AddressNormalizer())) {
            /**
             * Geocoding 失敗を再現するため、指定された例外を送出する。
             *
             * @param prefecture 都道府県
             * @param municipality 市区町村
             * @param district 丁目
             * @param addressDetails 番地以降の任意情報
             * @return 正常終了しないため返却値なし
             */
            @Override
            public GeoPoint getLatLng(
                    String prefecture,
                    String municipality,
                    String district,
                    String addressDetails) {
                throw geocodingException;
            }
        };
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            geocodeService,
            ReportParserTestSupport.createParser()
        );
        ListAppender<ILoggingEvent> appender = attachListAppender();

        try {
            ReportException exception = assertThrows(
                ReportException.class,
                () -> reportService.processReportMessage("line-user-id", VALID_REPORT_MESSAGE, 1792897200000L)
            );

            assertThat(exception.getStage()).isEqualTo(ReportProcessingStageEnum.GEOCODING);
            assertThat(exception.getItem()).isEqualTo("address");
            assertThat(exception.getDetail()).isEqualTo("福岡県福岡市中央1丁目1-1");
            assertThat(exception).hasCause(geocodingException);

            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=geocoding")
                .contains("item=address")
                .contains("value=福岡県福岡市中央1丁目1-1")
                .doesNotContain("line-user-id");
            assertThat(errorEvents.get(0).getThrowableProxy()).isNotNull();
        } finally {
            detachListAppender(appender);
        }
    }

    /**
     * DB保存失敗時に安全な構造化情報だけが一度記録され、登録値と原因チェーンが出ないことを確認する。
     */
    @Test
    void logsSafeDatabaseErrorDetailsOnce() {
        SQLException sqlException = new SQLException(
            "失敗行にline-user-idが含まれるメッセージ", "23502");
        ConstraintViolationException constraintViolationException = new ConstraintViolationException(
            "SQLと登録値を含み得るメッセージ",
            sqlException,
            "insert into reports ...",
            "reports_created_not_null"
        );
        DataIntegrityViolationException databaseException = new DataIntegrityViolationException(
            "登録値を含み得るメッセージ", constraintViolationException);
        ReportRepository reportRepository = reportRepositoryThrowingOnSave(databaseException);
        GeocodeService geocodeService = new GeocodeService(
                new RestTemplate(),
                new ObjectMapper(),
                new GeocodingAddressCandidateGenerator(new AddressNormalizer())) {
            /**
             * DB 保存処理まで進めるため、固定の座標情報を返す。
             *
             * @param prefecture 都道府県
             * @param municipality 市区町村
             * @param district 丁目
             * @param addressDetails 番地以降の任意情報
             * @return テスト用の固定座標
             */
            @Override
            public GeoPoint getLatLng(
                    String prefecture,
                    String municipality,
                    String district,
                    String addressDetails) {
                return new GeoPoint(33.5902, 130.4017);
            }
        };
        ReportService reportService = new ReportService(
            reportRepository,
            geocodeService,
            ReportParserTestSupport.createParser()
        );
        ListAppender<ILoggingEvent> appender = attachListAppender();

        try {
            assertThatThrownBy(() -> reportService.processReportMessage("line-user-id", VALID_REPORT_MESSAGE, 1792897200000L))
                .isInstanceOf(ReportException.class)
                .hasCause(databaseException);

            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=database_save")
                .contains("item=database")
                .contains("exceptionType=DataIntegrityViolationException")
                .contains("rootCauseType=SQLException")
                .contains("sqlState=23502")
                .contains("constraint=reports_created_not_null")
                .doesNotContain("失敗行に")
                .doesNotContain("SQLと登録値を")
                .doesNotContain("登録値を含み得る")
                .doesNotContain("occurDate=")
                .doesNotContain("prefecture=")
                .doesNotContain("municipality=")
                .doesNotContain("line-user-id")
                .doesNotContain("何年生");
            assertThat(errorEvents.get(0).getThrowableProxy()).isNull();
        } finally {
            detachListAppender(appender);
        }
    }

    /**
     * 番地以降がない場合に、空文字ではなく null が Entity へ設定されて保存されることを確認する。
     */
    @Test
    void savesNullWhenAddressDetailsIsMissing() {
        String messageWithoutAddressDetails = VALID_REPORT_MESSAGE.replace("天神1丁目1-1", "天神1丁目");
        AtomicReference<ReportEntity> savedReport = new AtomicReference<>();
        ReportRepository reportRepository = reportRepositoryCapturingSavedReport(savedReport);
        GeocodeService geocodeService = new GeocodeService(
                new RestTemplate(),
                new ObjectMapper(),
                new GeocodingAddressCandidateGenerator(new AddressNormalizer())) {
            /**
             * DB 保存処理まで進めるため、固定の座標情報を返す。
             *
             * @param prefecture 都道府県
             * @param municipality 市区町村
             * @param district 丁目
             * @param addressDetails 番地以降の任意情報
             * @return テスト用の固定座標
             */
            @Override
            public GeoPoint getLatLng(
                    String prefecture,
                    String municipality,
                    String district,
                    String addressDetails) {
                return new GeoPoint(33.5902, 130.4017);
            }
        };
        ReportService reportService = new ReportService(
            reportRepository,
            geocodeService,
            ReportParserTestSupport.createParser()
        );

        reportService.processReportMessage("line-user-id", messageWithoutAddressDetails, 1792897200000L);

        assertThat(savedReport.get()).isNotNull();
        assertThat(savedReport.get().getAddressDetails()).isNull();
    }

    /**
     * 住所4項目が正規化され、Geocoding とデータベース保存で同じ値を使用することを確認する。
     */
    @Test
    void usesNormalizedAddressItemsForGeocodingAndSave() {
        String messageWithFullWidthDigits = VALID_REPORT_MESSAGE
            .replace("天神1丁目1-1", "天神１丁目４番地５");
        AtomicReference<List<String>> geocodedAddressItems = new AtomicReference<>();
        AtomicReference<ReportEntity> savedReport = new AtomicReference<>();
        ReportRepository reportRepository = reportRepositoryCapturingSavedReport(savedReport);
        GeocodeService geocodeService = new GeocodeService(
                new RestTemplate(),
                new ObjectMapper(),
                new GeocodingAddressCandidateGenerator(new AddressNormalizer())) {
            /**
             * Geocoding に渡された住所項目を記録し、固定の座標情報を返す。
             *
             * @param prefecture 都道府県
             * @param municipality 市区町村
             * @param district 丁目
             * @param addressDetails 番地以降の任意情報
             * @return テスト用の固定座標
             */
            @Override
            public GeoPoint getLatLng(
                    String prefecture,
                    String municipality,
                    String district,
                    String addressDetails) {
                geocodedAddressItems.set(List.of(
                    prefecture,
                    municipality,
                    district,
                    addressDetails
                ));
                return new GeoPoint(33.5902, 130.4017);
            }
        };
        ReportService reportService = new ReportService(
            reportRepository,
            geocodeService,
            ReportParserTestSupport.createParser()
        );

        reportService.processReportMessage("line-user-id", messageWithFullWidthDigits, 1792897200000L);

        assertThat(geocodedAddressItems.get()).containsExactly(
            "福岡県",
            "福岡市中央区",
            "天神1丁目",
            "4-5"
        );
        assertThat(savedReport.get()).isNotNull();
        assertThat(savedReport.get().getPrefecture()).isEqualTo("福岡県");
        assertThat(savedReport.get().getMunicipality()).isEqualTo("福岡市中央区");
        assertThat(savedReport.get().getDistrict()).isEqualTo("天神1丁目");
        assertThat(savedReport.get().getAddressDetails()).isEqualTo("4-5");
    }

    /**
     * 呼び出されるとテストを失敗させる ReportRepository を生成する。
     *
     * @return 呼び出しを許可しない ReportRepository
     */
    private ReportRepository unusedReportRepository() {
        return (ReportRepository) Proxy.newProxyInstance(
            ReportRepository.class.getClassLoader(),
            new Class<?>[]{ReportRepository.class},
            (proxy, method, args) -> {
                throw new AssertionError("Repositoryは呼び出されない想定です: " + method.getName());
            }
        );
    }

    /**
     * 保存時に指定された例外を送出する ReportRepository を生成する。
     *
     * @param exception 保存時に送出する例外
     * @return 保存失敗を再現する ReportRepository
     */
    private ReportRepository reportRepositoryThrowingOnSave(RuntimeException exception) {
        return (ReportRepository) Proxy.newProxyInstance(
            ReportRepository.class.getClassLoader(),
            new Class<?>[]{ReportRepository.class},
            (proxy, method, args) -> {
                if (method.getName().equals("saveAndFlush")) {
                    throw exception;
                }
                throw new AssertionError("想定外のRepositoryメソッド呼び出しです: " + method.getName());
            }
        );
    }

    /**
     * 保存対象の Entity を記録し、その Entity を返す ReportRepository を生成する。
     *
     * @param savedReport 保存対象の Entity を記録する参照
     * @return 保存成功を再現する ReportRepository
     */
    private ReportRepository reportRepositoryCapturingSavedReport(
            AtomicReference<ReportEntity> savedReport) {
        return (ReportRepository) Proxy.newProxyInstance(
            ReportRepository.class.getClassLoader(),
            new Class<?>[]{ReportRepository.class},
            (proxy, method, args) -> {
                if (method.getName().equals("saveAndFlush")) {
                    ReportEntity report = (ReportEntity) args[0];
                    savedReport.set(report);
                    return report;
                }
                throw new AssertionError("想定外のRepositoryメソッド呼び出しです: " + method.getName());
            }
        );
    }

    /**
     * ReportService のロガーへ、ログイベント収集用 Appender を追加する。
     *
     * @return 起動してロガーへ追加済みの Appender
     */
    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ReportService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /**
     * ReportService のロガーから Appender を取り外して停止する。
     * レスポンス: なし。
     *
     * @param appender 取り外す Appender
     */
    private void detachListAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ReportService.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    /**
     * 収集したログイベントから ERROR レベルのイベントだけを抽出する。
     *
     * @param appender ログイベントを収集した Appender
     * @return ERROR レベルのログイベント一覧
     */
    private List<ILoggingEvent> errorEvents(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
    }
}
