package suspiciouspersonmap.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LineReplyServiceTest {

    // 実際のLINE APIには使用しない、テスト専用の認証情報。
    private static final String TEST_ACCESS_TOKEN = "test-channel-access-token";
    // リクエスト本文とログの漏えい確認に使うダミーの応答トークン。
    private static final String TEST_REPLY_TOKEN = "test-reply-token";
    // LINEの仕様で定められた応答メッセージAPIの送信先。
    private static final String REPLY_ENDPOINT = "https://api.line.me/v2/bot/message/reply";

    private RestTemplateBuilder restTemplateBuilder;
    private LineReplyService lineReplyService;
    private MockRestServiceServer mockServer;
    private Logger serviceLogger;
    private Logger httpLogger;
    private Level originalServiceLevel;
    private Level originalHttpLevel;
    private ListAppender<ILoggingEvent> logAppender;

    /**
     * 実際のHTTPクライアントの送受信先を差し替え、DEBUGログを含めた収集を準備する。
     * レスポンス: なし。
     */
    @BeforeEach
    void setUp() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        restTemplateBuilder = new RestTemplateBuilder(customizer);
        lineReplyService = new LineReplyService(restTemplateBuilder, TEST_ACCESS_TOKEN);
        mockServer = customizer.getServer();

        serviceLogger = (Logger) LoggerFactory.getLogger(LineReplyService.class);
        httpLogger = (Logger) LoggerFactory.getLogger(RestTemplate.class);
        originalServiceLevel = serviceLogger.getLevel();
        originalHttpLevel = httpLogger.getLevel();
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.setLevel(Level.DEBUG);
        httpLogger.setLevel(Level.DEBUG);
        serviceLogger.addAppender(logAppender);
        httpLogger.addAppender(logAppender);
    }

    /**
     * ログ収集とレベル変更を解除し、期待したHTTP通信が完了していることを確認する。
     * レスポンス: なし。
     */
    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        httpLogger.detachAppender(logAppender);
        serviceLogger.setLevel(originalServiceLevel);
        httpLogger.setLevel(originalHttpLevel);
        logAppender.stop();
        mockServer.verify();
    }

    /**
     * 認証ヘッダーとJSON本文で合意済みの固定文言を送り、API成功を返すことを確認する。
     */
    @Test
    void sendsRegistrationErrorWithAuthentication() {
        mockServer.expect(requestTo(REPLY_ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_ACCESS_TOKEN))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "replyToken": "test-reply-token",
                  "messages": [
                    {"type": "text", "text": "エラーが発生したため、不審者情報を登録できませんでした。"}
                  ]
                }
                """))
            .andRespond(withSuccess("{\"sentMessages\":[{\"id\":\"test-message-id\"}]}", MediaType.APPLICATION_JSON));

        assertThat(lineReplyService.replyRegistrationError(TEST_REPLY_TOKEN)).isTrue();
        assertThat(errorEvents()).isEmpty();
        assertThat(logAppender.list).isNotEmpty();
        assertNoSensitiveLogs();
    }

    /**
     * HTTP 400を代表に、エラー応答では再試行せず失敗を返し、応答本文の非公開値をログへ出さないことを確認する。
     */
    @Test
    void reportsHttpFailureWithoutLeakingResponse() {
        mockServer.expect(requestTo(REPLY_ENDPOINT))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body(TEST_ACCESS_TOKEN + " " + TEST_REPLY_TOKEN)
                .contentType(MediaType.TEXT_PLAIN));

        assertThat(lineReplyService.replyRegistrationError(TEST_REPLY_TOKEN)).isFalse();
        assertThat(errorEvents()).hasSize(1);
        assertThat(errorEvents().get(0).getFormattedMessage())
            .contains("stage=line_reply", "httpStatus=400");
        assertNoSensitiveLogs();
    }

    /**
     * タイムアウト・通信切断では再試行せず失敗を返し、原因例外の非公開値をログへ出さないことを確認する。
     *
     * @param failureType タイムアウトまたは通信切断
     */
    @ParameterizedTest
    @ValueSource(strings = {"timeout", "connection"})
    void reportsCommunicationFailureWithoutLeakingCause(String failureType) {
        String failureMessage = TEST_ACCESS_TOKEN + " " + TEST_REPLY_TOKEN;
        IOException failure = failureType.equals("timeout")
            ? new SocketTimeoutException(failureMessage)
            : new IOException(failureMessage);
        mockServer.expect(requestTo(REPLY_ENDPOINT)).andRespond(withException(failure));

        assertThat(lineReplyService.replyRegistrationError(TEST_REPLY_TOKEN)).isFalse();
        assertThat(errorEvents()).hasSize(1);
        assertThat(errorEvents().get(0).getFormattedMessage())
            .contains("stage=line_reply", "httpStatus=not_available", "exceptionType=ResourceAccessException");
        assertNoSensitiveLogs();
    }

    /**
     * リダイレクト応答を成功として扱わず、追加送信せずに失敗を記録することを確認する。
     */
    @Test
    void reportsUnexpectedRedirectAsFailure() {
        // 302はRestTemplateの標準設定では例外にならないため、catchを通らず、
        // is2xxSuccessful()がfalseとなって失敗を返す経路を確認する。
        mockServer.expect(requestTo(REPLY_ENDPOINT))
            .andRespond(withStatus(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "https://example.invalid/reply"));

        assertThat(lineReplyService.replyRegistrationError(TEST_REPLY_TOKEN)).isFalse();
        assertThat(errorEvents()).hasSize(1);
        assertThat(errorEvents().get(0).getFormattedMessage()).contains("httpStatus=302");
        assertNoSensitiveLogs();
    }

    /**
     * 応答トークンがnull・空文字・空白のみの場合、HTTP送信せず失敗を記録することを確認する。
     *
     * @param replyToken 欠落または空の応答トークン
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void doesNotSendWithoutReplyToken(String replyToken) {
        assertThat(lineReplyService.replyRegistrationError(replyToken)).isFalse();
        assertThat(errorEvents()).hasSize(1);
        assertThat(errorEvents().get(0).getFormattedMessage()).contains("reason=missing_reply_token");
        assertNoSensitiveLogs();
    }

    /**
     * アクセストークンがnull・空文字・空白のみの場合、実値を含めず生成時に拒否することを確認する。
     *
     * @param accessToken 欠落または空のアクセストークン
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingAccessToken(String accessToken) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new LineReplyService(restTemplateBuilder, accessToken));

        assertThat(exception.getMessage()).isEqualTo("LINEチャネルアクセストークンは必須です");
    }

    /**
     * 収集したログに認証情報・応答トークン・原因例外が含まれないことを確認する。
     * レスポンス: なし。
     */
    private void assertNoSensitiveLogs() {
        assertThat(logAppender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(TEST_ACCESS_TOKEN, TEST_REPLY_TOKEN);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    /**
     * 収集したログからERRORレベルの記録を取得する。
     *
     * @return ERRORログの一覧
     */
    private List<ILoggingEvent> errorEvents() {
        return logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }
}
