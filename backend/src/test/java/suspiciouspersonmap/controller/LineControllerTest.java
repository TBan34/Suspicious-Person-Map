package suspiciouspersonmap.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import suspiciouspersonmap.entity.ReportEntity;
import suspiciouspersonmap.model.GeoPoint;
import suspiciouspersonmap.repository.ReportRepository;
import suspiciouspersonmap.service.GeocodeService;
import suspiciouspersonmap.service.LineWebhookSignatureVerifier;
import suspiciouspersonmap.service.ReportParserTestSupport;
import suspiciouspersonmap.service.ReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineControllerTest {

    private static final String TEST_CHANNEL_SECRET = "test-channel-secret";
    private static final String VALID_REPORT_MESSAGE = "2025年9月8日午後6時10分、福岡市中央区天神1丁目1-1付近の路上で、"
        + "女性が男から「何年生？」と声をかけられました。";
    private static final String VALID_WEBHOOK_BODY = """
        {
          "events": [
            {
              "timestamp": 1792897200000,
              "source": {"userId": "line-user-id"},
              "message": {
                "text": "2025年9月8日午後6時10分、福岡市中央区天神1丁目1-1付近の路上で、女性が男から「何年生？」と声をかけられました。"
              }
            }
          ]
        }
        """;

    /**
     * 署名済みイベントの時刻欠落・型不正・Long範囲外をControllerで一度だけ記録し、保存しないことを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "\"1792897200000\"", "1.5", "true", "{}", "9223372036854775808"})
    void rejectsInvalidSignedEventTimestamp(String timestampJson) {
        String body = timestampJson.equals("missing")
            ? VALID_WEBHOOK_BODY.replace("\"timestamp\": 1792897200000,", "")
            : VALID_WEBHOOK_BODY.replace("1792897200000", timestampJson);
        ReportService service = new ReportService(unusedReportRepository(), null,
            ReportParserTestSupport.createParser());
        LineController controller = createController(service);
        ListAppender<ILoggingEvent> serviceAppender = attachListAppender(ReportService.class);
        ListAppender<ILoggingEvent> controllerAppender = attachListAppender(LineController.class);
        try {
            ResponseEntity<String> response = controller.callback(body, createSignature(body));
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo("Internal Server Error");
            assertThat(errorEvents(serviceAppender)).isEmpty();
            assertThat(errorEvents(controllerAppender)).hasSize(1);
            assertThat(errorEvents(controllerAppender).get(0).getFormattedMessage())
                .contains("error=INVALID_DATETIME", "item=eventTimestamp", "イベントタイムスタンプ")
                .doesNotContain("line-user-id", body, createSignature(body));
            assertThat(errorEvents(controllerAppender).get(0).getThrowableProxy()).isNull();
        } finally {
            detachListAppender(ReportService.class, serviceAppender);
            detachListAppender(LineController.class, controllerAppender);
        }
    }

    /**
     * JSON上でLong型として取得できるイベント時刻は、値による日時範囲を制限せずServiceへ渡すことを確認する。
     *
     * @param eventTimestamp LINEイベント時刻としてServiceへ渡すLong値
     */
    @ParameterizedTest
    @ValueSource(longs = {-1L, Long.MAX_VALUE})
    void passesLongEventTimestampToService(long eventTimestamp) {
        String body = VALID_WEBHOOK_BODY.replace("1792897200000", Long.toString(eventTimestamp));
        RecordingReportService reportService = new RecordingReportService();
        LineController controller = createController(reportService);

        ResponseEntity<String> response = controller.callback(body, createSignature(body));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(reportService.callCount).isEqualTo(1);
        assertThat(reportService.receivedTimestamp).isEqualTo(eventTimestamp);
    }

    /**
     * 署名済みの不審者情報メッセージを実際の解析器へ渡し、イベント日時でうるう年を推定して保存することを確認する。
     */
    @Test
    void savesNaturalReportUsingSignedEventTime() throws Exception {
        ReportRepository reportRepository = mock(ReportRepository.class);
        when(reportRepository.saveAndFlush(any(ReportEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        GeocodeService geocodeService = mock(GeocodeService.class);
        when(geocodeService.getLatLng(anyString(), anyString(), anyString(), any()))
            .thenReturn(new GeoPoint(33.5, 130.4));
        ObjectNode root =
            (ObjectNode) new ObjectMapper().readTree(VALID_WEBHOOK_BODY);
        ObjectNode event =
            (ObjectNode) root.path("events").get(0);
        event.put("timestamp", OffsetDateTime.parse("2026-03-01T12:00:00+09:00").toInstant().toEpochMilli());
        ((ObjectNode) event.path("message")).put("text",
            "2月29日午後9時、福岡市南区高宮1丁目付近の路上で、女性が男につきまとわれました。");
        String body = root.toString();
        LineController controller = createController(new ReportService(reportRepository, geocodeService,
            ReportParserTestSupport.createParser()));
        assertThat(controller.callback(body, createSignature(body)).getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<ReportEntity> reportCaptor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getOccurDate()).isEqualTo("2024-02-29T21:00:00");
        assertThat(reportCaptor.getValue().getTag1()).isEqualTo("つきまとい");
    }

    /**
     * 不正JSONに含まれる非公開値が、Jacksonの例外メッセージを経由してログへ漏れないことを確認する。
     */
    @Test
    void doesNotLogMalformedJsonContents() {
        String body = "{\"events\":private-line-user-id-secret}";
        LineController controller = createController(new RecordingReportService());
        ListAppender<ILoggingEvent> appender = attachListAppender(LineController.class);
        try {
            assertThat(controller.callback(body, createSignature(body)).getStatusCode().value()).isEqualTo(500);
            assertThat(errorEvents(appender)).hasSize(1);
            assertThat(errorEvents(appender).get(0).getFormattedMessage()).doesNotContain("private-line-user-id-secret");
            assertThat(errorEvents(appender).get(0).getThrowableProxy()).isNull();
        } finally {
            detachListAppender(LineController.class, appender);
        }
    }

    /**
     * 正常な署名の Webhook だけが ReportService へ渡されることを確認する。
     */
    @Test
    void processesWebhookWithValidSignature() {
        RecordingReportService reportService = new RecordingReportService();
        LineController controller = createController(reportService);

        ResponseEntity<String> response = controller.callback(
            VALID_WEBHOOK_BODY,
            createSignature(VALID_WEBHOOK_BODY)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("OK");
        assertThat(reportService.callCount).isEqualTo(1);
        assertThat(reportService.receivedUserId).isEqualTo("line-user-id");
        assertThat(reportService.receivedText).isEqualTo(VALID_REPORT_MESSAGE);
        assertThat(reportService.receivedTimestamp).isEqualTo(1792897200000L);
    }

    /**
     * 署名がない Webhook を拒否し、ReportService を呼び出さないことを確認する。
     */
    @Test
    void rejectsWebhookWithoutSignature() {
        RecordingReportService reportService = new RecordingReportService();
        LineController controller = createController(reportService);

        ResponseEntity<String> response = controller.callback(VALID_WEBHOOK_BODY, null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("Bad Request");
        assertThat(reportService.callCount).isZero();
    }

    /**
     * 不正な署名の Webhook を JSON 解析前に拒否し、ReportService を呼び出さないことを確認する。
     */
    @Test
    void rejectsWebhookWithInvalidSignature() {
        RecordingReportService reportService = new RecordingReportService();
        LineController controller = createController(reportService);

        ResponseEntity<String> response = controller.callback(
            "{",
            createSignature(VALID_WEBHOOK_BODY)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("Bad Request");
        assertThat(reportService.callCount).isZero();
    }

    /**
     * 正常な署名の空イベントを受け取った場合に、保存処理を行わず正常応答することを確認する。
     */
    @Test
    void acceptsSignedWebhookWithoutEvents() {
        String body = "{\"events\":[]}";
        RecordingReportService reportService = new RecordingReportService();
        LineController controller = createController(reportService);

        ResponseEntity<String> response = controller.callback(body, createSignature(body));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("None");
        assertThat(reportService.callCount).isZero();
    }

    /**
     * ReportService で記録済みの例外を重複出力せず、内部情報を含まない固定レスポンスを返すことを確認する。
     */
    @Test
    void doesNotLogReportExceptionTwice() {
        GeocodeService geocodeService = mock(GeocodeService.class);
        when(geocodeService.getLatLng(anyString(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Geocoding失敗"));
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            geocodeService,
            ReportParserTestSupport.createParser()
        );
        LineController controller = createController(reportService);
        ListAppender<ILoggingEvent> serviceAppender = attachListAppender(ReportService.class);
        ListAppender<ILoggingEvent> controllerAppender = attachListAppender(LineController.class);

        try {
            ResponseEntity<String> response = controller.callback(
                VALID_WEBHOOK_BODY,
                createSignature(VALID_WEBHOOK_BODY)
            );

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo("Internal Server Error");
            assertThat(response.getBody()).doesNotContain("Geocoding失敗");
            assertThat(errorEvents(serviceAppender)).hasSize(1);
            assertThat(errorEvents(controllerAppender)).isEmpty();
        } finally {
            detachListAppender(ReportService.class, serviceAppender);
            detachListAppender(LineController.class, controllerAppender);
        }
    }

    /**
     * 不正な Webhook JSON の解析失敗時に、本文を含めず解析段階だけが記録されることを確認する。
     */
    @Test
    void logsWebhookJsonParsingStage() {
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            null,
            ReportParserTestSupport.createParser()
        );
        LineController controller = createController(reportService);
        ListAppender<ILoggingEvent> appender = attachListAppender(LineController.class);

        try {
            ResponseEntity<String> response = controller.callback("{", createSignature("{"));

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo("Internal Server Error");
            List<ILoggingEvent> errorEvents = errorEvents(appender);
            assertThat(errorEvents).hasSize(1);
            assertThat(errorEvents.get(0).getFormattedMessage())
                .contains("stage=webhook_json_parsing");
            assertThat(errorEvents.get(0).getThrowableProxy()).isNull();
        } finally {
            detachListAppender(LineController.class, appender);
        }
    }

    /**
     * テスト用の署名検証サービスを設定した LineController を生成する。
     *
     * @param reportService Controller から呼び出す ReportService
     * @return テスト用の LineController
     */
    private LineController createController(ReportService reportService) {
        return new LineController(
            reportService,
            new LineWebhookSignatureVerifier(TEST_CHANNEL_SECRET),
            new ObjectMapper()
        );
    }

    /**
     * テスト用チャネルシークレットと本文から X-Line-Signature の値を生成する。
     *
     * @param body 署名対象の Webhook 本文
     * @return Base64 エンコード済みの署名
     */
    private String createSignature(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                TEST_CHANNEL_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            return Base64.getEncoder().encodeToString(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("テスト用署名の生成に失敗しました", e);
        }
    }

    /**
     * Controller から受け取った値と呼び出し回数を記録する ReportService。
     */
    private static class RecordingReportService extends ReportService {

        private int callCount;
        private String receivedUserId;
        private String receivedText;
        private Long receivedTimestamp;

        /**
         * 依存先を使用せず呼び出し内容だけを記録する ReportService を生成する。
         */
        RecordingReportService() {
            super(null, null, null);
        }

        /**
         * Controller から渡されたユーザー ID とメッセージを記録する。
         * レスポンス: なし。
         *
         * @param userId 登録元の LINE ユーザー ID
         * @param text 不審者情報メッセージ
         * @param eventTimestamp LINEイベント時刻（ミリ秒）
         */
        @Override
        public void processReportMessage(String userId, String text, Long eventTimestamp) {
            callCount++;
            receivedUserId = userId;
            receivedText = text;
            receivedTimestamp = eventTimestamp;
        }
    }

    /**
     * 呼び出されるとテストを失敗させる ReportRepository を生成する。
     *
     * @return 呼び出しを許可しない ReportRepository
     */
    private ReportRepository unusedReportRepository() {
        return mock(ReportRepository.class);
    }

    /**
     * 指定したクラスのロガーへ、ログイベント収集用 Appender を追加する。
     *
     * @param loggerClass 収集対象のロガークラス
     * @return 起動してロガーへ追加済みの Appender
     */
    private ListAppender<ILoggingEvent> attachListAppender(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /**
     * 指定したクラスのロガーから Appender を取り外して停止する。
     * レスポンス: なし。
     *
     * @param loggerClass 収集対象のロガークラス
     * @param appender 取り外す Appender
     */
    private void detachListAppender(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
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
