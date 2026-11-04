package suspiciouspersonmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.INVALID_ADDRESS;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.INVALID_DATETIME;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.INVALID_SUMMARY;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.TOO_MANY_TAGS;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.UNCLASSIFIABLE_TAG;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.UNSUPPORTED_DATETIME;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.entity.ReportEntity;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.model.GeoPoint;
import suspiciouspersonmap.repository.ReportRepository;

/** 実際の解析器とServiceを組み合わせ、外部境界と安全なログを検証する。 */
class ReportServiceParsingTest {
    private static final long EVENT_TIMESTAMP = OffsetDateTime
        .parse("2026-10-25T10:00:00+09:00").toInstant().toEpochMilli();
    private static final String VALID_REPORT_MESSAGE = "1月4日午後9時、福岡市南区高宮1丁目11番"
        + "付近の路上で、女性が男につきまとわれました。";

    /** 解析拒否ケースの入力と期待エラー。 */
    private record RejectionCase(
        String testName,
        String message,
        Long eventTimestamp,
        ReportErrorEnum expectedError
    ) {}

    /**
     * 各解析エラーでGeocoding・保存を呼ばず、安全なERRORログが一度だけ出ることを確認する。
     * @return 解析拒否ケースごとの動的テスト
     */
    @TestFactory
    List<DynamicTest> rejectsBeforeExternalBoundariesAndLogsOnce() {
        List<RejectionCase> rejectionCases = createRejectionCases();
        return rejectionCases.stream().map(rejectionCase ->
            DynamicTest.dynamicTest(rejectionCase.testName(), () -> {
                ReportRepository reportRepository = mock(ReportRepository.class);
                GeocodeService geocodeService = mock(GeocodeService.class);
                ReportService reportService = new ReportService(
                    reportRepository, geocodeService, ReportParserTestSupport.createParser());
                Logger logger = (Logger) LoggerFactory.getLogger(ReportService.class);
                ListAppender<ILoggingEvent> appender = new ListAppender<>();
                appender.start();
                logger.addAppender(appender);
                try {
                    ReportException error = assertThrows(
                        ReportException.class,
                        () -> reportService.processReportMessage(
                            "private-line-user-id",
                            rejectionCase.message(),
                            rejectionCase.eventTimestamp()
                        )
                    );
                    assertThat(error.getErrorType()).isEqualTo(rejectionCase.expectedError());
                    List<ILoggingEvent> errorEvents = appender.list.stream()
                        .filter(event -> event.getLevel() == Level.ERROR)
                        .toList();
                    assertThat(errorEvents).hasSize(1);
                    assertThat(errorEvents.get(0).getFormattedMessage())
                        .contains(
                            "stage=report_message_transformation",
                            "error=" + rejectionCase.expectedError(),
                            "item=",
                            "reason="
                        )
                        .doesNotContain("private-line-user-id", rejectionCase.message());
                    assertThat(errorEvents.get(0).getThrowableProxy()).isNull();
                    if (rejectionCase.eventTimestamp() == null
                            || rejectionCase.eventTimestamp() == -1L) {
                        assertThat(errorEvents.get(0).getFormattedMessage())
                            .contains("item=eventTimestamp", "イベントタイムスタンプが");
                    }
                    verifyNoInteractions(geocodeService, reportRepository);
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            })
        ).toList();
    }

    /**
     * 解析拒否の日時・住所・概要・タグ境界ケースを作る。
     * @return 解析拒否ケース
     */
    private List<RejectionCase> createRejectionCases() {
        List<RejectionCase> rejectionCases = new ArrayList<>();
        for (String dateTimeText : List.of(
                "2026年2月29日午後9時",
                "2026年4月31日午後9時",
                "10月25日午後9時",
                "2026年10月25日午後9時",
                "夕方ころ",
                "")) {
            rejectionCases.add(new RejectionCase(
                "日時不正/" + dateTimeText,
                VALID_REPORT_MESSAGE.replace("1月4日午後9時", dateTimeText),
                EVENT_TIMESTAMP,
                INVALID_DATETIME
            ));
        }
        rejectionCases.add(new RejectionCase(
            "期間",
            VALID_REPORT_MESSAGE.replace("1月4日午後9時", "1月4日午後9時から午後10時まで"),
            EVENT_TIMESTAMP,
            UNSUPPORTED_DATETIME
        ));
        rejectionCases.add(new RejectionCase(
            "イベント時刻欠落", VALID_REPORT_MESSAGE, null, INVALID_DATETIME));
        rejectionCases.add(new RejectionCase(
            "イベント時刻不正", VALID_REPORT_MESSAGE, -1L, INVALID_DATETIME));
        rejectionCases.add(new RejectionCase(
            "概要空",
            VALID_REPORT_MESSAGE.replace("女性が男につきまとわれました。", ""),
            EVENT_TIMESTAMP,
            INVALID_SUMMARY
        ));
        rejectionCases.add(new RejectionCase(
            "住所不正",
            VALID_REPORT_MESSAGE.replace("福岡市南区", ""),
            EVENT_TIMESTAMP,
            INVALID_ADDRESS
        ));
        for (String htmlCharacterReference : List.of("&amp;", "&#12354;", "&#x3042;")) {
            rejectionCases.add(new RejectionCase(
                "日時文字参照",
                VALID_REPORT_MESSAGE.replace("1月4日", "1月" + htmlCharacterReference + "4日"),
                EVENT_TIMESTAMP,
                INVALID_DATETIME
            ));
            rejectionCases.add(new RejectionCase(
                "住所文字参照",
                VALID_REPORT_MESSAGE.replace("高宮", "高" + htmlCharacterReference + "宮"),
                EVENT_TIMESTAMP,
                INVALID_ADDRESS
            ));
            rejectionCases.add(new RejectionCase(
                "概要文字参照",
                VALID_REPORT_MESSAGE + htmlCharacterReference,
                EVENT_TIMESTAMP,
                INVALID_SUMMARY
            ));
        }
        rejectionCases.add(new RejectionCase(
            "タグ超過",
            VALID_REPORT_MESSAGE.replace(
                "女性が男につきまとわれました。",
                "男が女性を追いかけ、下半身を露出し、卑わいな言葉をかけ、包丁を持っていました。"
            ),
            EVENT_TIMESTAMP,
            TOO_MANY_TAGS
        ));
        rejectionCases.add(new RejectionCase(
            "分類不能",
            VALID_REPORT_MESSAGE.replace("女性が男につきまとわれました。", "男が歩いていました。"),
            EVENT_TIMESTAMP,
            UNCLASSIFIABLE_TAG
        ));
        return rejectionCases;
    }

    /**
     * 文章の解析結果について、Geocodingへ渡した住所と保存内容が一致することを確認する。
     */
    @Test
    void savesParsedReport() {
        ReportRepository reportRepository = mock(ReportRepository.class);
        GeocodeService geocodeService = mock(GeocodeService.class);
        when(geocodeService.getLatLng("福岡県", "福岡市南区", "高宮1丁目", "11番"))
            .thenReturn(new GeoPoint(33.5, 130.4));
        when(reportRepository.saveAndFlush(any(ReportEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReportService reportService = new ReportService(
            reportRepository, geocodeService, ReportParserTestSupport.createParser());
        reportService.processReportMessage(
            "private-line-user-id", VALID_REPORT_MESSAGE, EVENT_TIMESTAMP);

        verify(geocodeService).getLatLng("福岡県", "福岡市南区", "高宮1丁目", "11番");
        ArgumentCaptor<ReportEntity> reportCaptor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        ReportEntity savedReport = reportCaptor.getValue();
        assertThat(savedReport.getDistrict()).isEqualTo("高宮1丁目");
        assertThat(savedReport.getAddressDetails()).isEqualTo("11番");
        assertThat(savedReport.getOccurDate()).isEqualTo("2026-01-04T21:00:00");
        assertThat(savedReport.getSummary()).isEqualTo("女性が男につきまとわれました。");
        assertThat(savedReport.getTag1()).isEqualTo("つきまとい");
        assertThat(savedReport.getTag2()).isNull();
        assertThat(savedReport.getTag3()).isNull();
        assertThat(savedReport.getLatitude()).isEqualTo(33.5);
        assertThat(savedReport.getLongitude()).isEqualTo(130.4);
    }
}
