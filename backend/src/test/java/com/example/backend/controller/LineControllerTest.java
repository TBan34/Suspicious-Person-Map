package com.example.backend.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.backend.model.GeoPoint;
import com.example.backend.repository.ReportRepository;
import com.example.backend.service.AddressNormalizer;
import com.example.backend.service.GeocodeService;
import com.example.backend.service.GeocodingAddressCandidateGenerator;
import com.example.backend.service.LineWebhookSignatureVerifier;
import com.example.backend.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineControllerTest {

    private static final String TEST_CHANNEL_SECRET = "test-channel-secret";
    private static final String VALID_REPORT_MESSAGE = "タグ:声かけ\n"
        + "日時:2025年9月8日午後6時10分\n"
        + "都道府県:福岡県\n"
        + "市区町村:福岡市\n"
        + "丁目:中央1丁目\n"
        + "番地以降:1-1\n"
        + "概要:テスト用の概要";
    private static final String VALID_WEBHOOK_BODY = """
        {
          "events": [
            {
              "source": {"userId": "line-user-id"},
              "message": {
                "text": "タグ:声かけ\\n日時:2025年9月8日午後6時10分\\n都道府県:福岡県\\n市区町村:福岡市\\n丁目:中央1丁目\\n番地以降:1-1\\n概要:テスト用の概要"
              }
            }
          ]
        }
        """;

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
    void doesNotLogReportProcessingExceptionTwice() {
        GeocodeService geocodeService = new GeocodeService(
                new RestTemplate(),
                new ObjectMapper(),
                new GeocodingAddressCandidateGenerator(new AddressNormalizer())) {
            /**
             * Geocoding 失敗を再現するため、常に例外を送出する。
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
                throw new RuntimeException("Geocoding失敗");
            }
        };
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            geocodeService,
            new AddressNormalizer()
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
     * 不正な Webhook JSON の解析失敗時に、解析段階と例外がエラーログへ出力されることを確認する。
     */
    @Test
    void logsWebhookJsonParsingStage() {
        ReportService reportService = new ReportService(
            unusedReportRepository(),
            null,
            new AddressNormalizer()
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
            assertThat(errorEvents.get(0).getThrowableProxy()).isNotNull();
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
            new LineWebhookSignatureVerifier(TEST_CHANNEL_SECRET)
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

        /**
         * 依存先を使用せず呼び出し内容だけを記録する ReportService を生成する。
         * レスポンス: 生成されたテスト用 ReportService。
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
         */
        @Override
        public void processReportMessage(String userId, String text) {
            callCount++;
            receivedUserId = userId;
            receivedText = text;
        }
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
