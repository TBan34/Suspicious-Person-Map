package suspiciouspersonmap.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import suspiciouspersonmap.entity.ReportEntity;
import suspiciouspersonmap.exception.GeocodingException;
import suspiciouspersonmap.model.GeoPoint;
import suspiciouspersonmap.repository.ReportRepository;
import suspiciouspersonmap.service.GeocodeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class LineWebhookProcessingTest {

    // test Profileの署名検証に使用するダミーのチャネルシークレット。
    private static final String TEST_CHANNEL_SECRET = "test-line-channel-secret";
    // 既存のControllerテストで登録可能なことを確認している不審者情報。
    private static final String VALID_REPORT_MESSAGE = "2025年9月8日午後6時10分、福岡市中央区天神1丁目1-1付近の路上で、"
        + "女性が男から「何年生？」と声をかけられました。";

    @Autowired
    private LineController lineController;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeocodeService geocodeService;

    private ListAppender<ILoggingEvent> logAppender;

    /**
     * DBを空にし、外部Geocodingの代替とアプリケーションログの収集を準備する。
     * レスポンス: なし。
     */
    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        when(geocodeService.getLatLng(anyString(), anyString(), anyString(), any()))
            .thenReturn(new GeoPoint(33.5, 130.4));
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger("suspiciouspersonmap")).addAppender(logAppender);
    }

    /**
     * ログ収集を解除し、後続テストへ登録データを残さない。
     * レスポンス: なし。
     */
    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("suspiciouspersonmap")).detachAppender(logAppender);
        logAppender.stop();
        reportRepository.deleteAll();
    }

    /**
     * 同じWebhookに含まれる正常イベントをすべて保存することを確認する。
     */
    @Test
    void savesAllValidEvents() throws Exception {
        processEvents(createEvent("sender-first"), createEvent("sender-second"), createEvent("sender-third"));

        assertThat(reportRepository.findAll()).extracting(ReportEntity::getUserId)
            .containsExactlyInAnyOrder("sender-first", "sender-second", "sender-third");
        assertThat(errorEvents()).isEmpty();
    }

    /**
     * 途中のイベントが失敗しても、前後の正常イベントが独立したトランザクションで保存されることを確認する。
     *
     * @param failureType 失敗させるイベントの処理箇所
     * @param expectedStage エラーログに記録される処理段階
     */
    @ParameterizedTest
    @CsvSource({
        "timestamp,report_message_transformation",
        "parsing,report_message_transformation",
        "geocoding,geocoding",
        "database,database_save",
        "commit,webhook_event_processing"
    })
    void savesValidEventsBeforeAndAfterFailure(String failureType, String expectedStage) throws Exception {
        ObjectNode failedEvent = createEvent("sender-failed");
        switch (failureType) {
            case "timestamp" -> failedEvent.remove("timestamp");
            case "parsing" -> ((ObjectNode) failedEvent.path("message")).put("text",
                VALID_REPORT_MESSAGE.replace("2025年9月8日", "2025年2月30日"));
            case "geocoding" -> when(geocodeService.getLatLng(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GeoPoint(33.5, 130.4))
                .thenThrow(new GeocodingException("test geocoding failure", "test address"))
                .thenReturn(new GeoPoint(33.5, 130.4));
            // Entityのuser_id列（255文字）の制約違反で実際のDB保存を失敗させる。
            case "database" -> ((ObjectNode) failedEvent.path("source")).put("userId", "x".repeat(256));
            case "commit" -> when(geocodeService.getLatLng(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GeoPoint(33.5, 130.4))
                .thenAnswer(invocation -> geocodeWithCommitFailure())
                .thenReturn(new GeoPoint(33.5, 130.4));
            default -> throw new IllegalArgumentException("未知のテストケースです");
        }

        processEvents(createEvent("sender-before"), failedEvent, createEvent("sender-after"));

        assertThat(reportRepository.findAll()).extracting(ReportEntity::getUserId)
            .containsExactlyInAnyOrder("sender-before", "sender-after");
        assertThat(errorEvents()).hasSize(1);
        assertThat(errorEvents().get(0).getFormattedMessage()).contains("stage=" + expectedStage)
            .doesNotContain("sender-before", "sender-failed", "sender-after", "x".repeat(256), "private-test-value");
        if (failureType.equals("commit")) {
            assertThat(errorEvents().get(0).getThrowableProxy()).isNull();
        }
    }

    /**
     * 登録項目を持たないイベントを無視した後も、正常イベントを保存することを確認する。
     */
    @Test
    void savesValidEventAfterIgnoredEvent() throws Exception {
        processEvents(objectMapper.createObjectNode(), createEvent("sender-after"));

        assertThat(reportRepository.findAll()).extracting(ReportEntity::getUserId)
            .containsExactly("sender-after");
        assertThat(errorEvents()).isEmpty();
    }

    /**
     * 全イベントが失敗する場合も各イベントのエラーを一度ずつ記録し、保存しないことを確認する。
     */
    @Test
    void logsEachFailureWhenAllEventsFail() throws Exception {
        ObjectNode firstEvent = createEvent("sender-first");
        firstEvent.remove("timestamp");
        ObjectNode secondEvent = createEvent("sender-second");
        secondEvent.put("timestamp", "invalid-timestamp");

        processEvents(firstEvent, secondEvent);

        assertThat(reportRepository.findAll()).isEmpty();
        assertThat(errorEvents()).hasSize(2).allSatisfy(event ->
            assertThat(event.getFormattedMessage()).contains("error=INVALID_DATETIME")
                .doesNotContain("sender-first", "sender-second", "invalid-timestamp"));
    }

    /**
     * 既存の解析規則で登録できる単一イベントを生成する。
     *
     * @param userId 登録元を識別するテスト用ユーザーID
     * @return 登録可能なイベントJSON
     */
    private ObjectNode createEvent(String userId) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("timestamp", 1792897200000L);
        event.putObject("source").put("userId", userId);
        event.putObject("message").put("text", VALID_REPORT_MESSAGE);
        return event;
    }

    /**
     * Geocodingの結果を返し、現在のトランザクションのコミット直前に障害を発生させる。
     *
     * @return テスト用の座標
     */
    private GeoPoint geocodeWithCommitFailure() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * コミット直前の障害を再現し、保存済みの行もロールバックさせる。レスポンス: なし。
             *
             * @param readOnlyFlg 読み取り専用トランザクションかどうか
             */
            @Override
            public void beforeCommit(boolean readOnlyFlg) {
                throw new TransactionSystemException("test commit failure: private-test-value");
            }
        });
        return new GeoPoint(33.5, 130.4);
    }

    /**
     * イベントを一つの署名付きWebhookへまとめ、実際のControllerで処理する。
     * レスポンス: なし。
     *
     * @param events 同じWebhookへ含めるイベント
     */
    private void processEvents(JsonNode... events) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode eventArray = root.putArray("events");
        for (JsonNode event : events) {
            eventArray.add(event);
        }
        String body = objectMapper.writeValueAsString(root);
        lineController.callback(body, createSignature(body));
    }

    /**
     * test ProfileのダミーシークレットでWebhook本文に署名する。
     *
     * @param body 署名対象のWebhook本文
     * @return Base64形式の署名
     */
    private String createSignature(String body) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_CHANNEL_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * アプリケーションが記録したERRORログを取得する。
     *
     * @return ERRORレベルのログ一覧
     */
    private List<ILoggingEvent> errorEvents() {
        return logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }
}
