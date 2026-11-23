package suspiciouspersonmap.service;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * LINE応答メッセージAPIで、不審者情報の登録エラーを返信する。
 */
@Slf4j
public class LineReplyService {

    // LINE応答メッセージAPIの送信先。
    private static final URI REPLY_ENDPOINT = URI.create("https://api.line.me/v2/bot/message/reply");
    // 登録失敗時に返信する、合意済みの共通文言。
    private static final String REGISTRATION_ERROR_MESSAGE = "エラーが発生したため、不審者情報を登録できませんでした。";
    // LINEへの接続確立を待つ時間の上限。
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // LINEからの応答読み取りを待つ時間の上限。
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final String channelAccessToken;

    /**
     * タイムアウトを設定した専用HTTPクライアントと認証情報を持つLINE返信サービスを生成する。
     *
     * @param restTemplateBuilder 専用HTTPクライアントの生成元
     * @param channelAccessToken LINE Messaging APIのチャネルアクセストークン
     */
    public LineReplyService(RestTemplateBuilder restTemplateBuilder, String channelAccessToken) {
        if (StringUtils.isBlank(channelAccessToken)) {
            throw new IllegalArgumentException("LINEチャネルアクセストークンは必須です");
        }
        this.channelAccessToken = channelAccessToken;
        this.restTemplate = restTemplateBuilder
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .redirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW)
            .build();
    }

    /**
     * 登録失敗の固定文言を返信し、送信できない場合は非公開値を含めずログへ記録する。
     *
     * @param replyToken 返信対象イベントの応答トークン
     * @return LINE APIが送信要求を受け付けた場合はtrue、トークン欠落または通信失敗時はfalse
     */
    public boolean replyRegistrationError(String replyToken) {
        if (StringUtils.isBlank(replyToken)) {
            log.error("LINEエラー返信を送信できませんでした。stage=line_reply, reason=missing_reply_token");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channelAccessToken);
        ReplyRequest request = new ReplyRequest(replyToken,
            List.of(Map.of("type", "text", "text", REGISTRATION_ERROR_MESSAGE)));

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                REPLY_ENDPOINT, new HttpEntity<>(request, headers), Void.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
            log.error("LINEエラー返信に失敗しました。stage=line_reply, httpStatus={}",
                response.getStatusCode().value());
        } catch (RestClientException e) {
            // HTTP応答本文や通信例外の原因には認証情報・応答トークンが含まれ得るため出力しない。
            String httpStatus = e instanceof RestClientResponseException responseException
                ? Integer.toString(responseException.getStatusCode().value())
                : "not_available";
            log.error("LINEエラー返信に失敗しました。stage=line_reply, httpStatus={}, exceptionType={}",
                httpStatus, e.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * LINE APIに送る本文。HTTPクライアントのDEBUGログにも応答トークンを出さない。
     *
     * @param replyToken 返信対象イベントの応答トークン
     * @param messages 送信するメッセージ
     */
    private record ReplyRequest(String replyToken, List<Map<String, String>> messages) {

        /**
         * HTTPクライアントが本文をログへ出す際に、値を含まない表現を返す。
         *
         * @return 応答トークンを含まないリクエストの説明
         */
        @Override
        public String toString() {
            return "LINE reply request";
        }
    }
}
