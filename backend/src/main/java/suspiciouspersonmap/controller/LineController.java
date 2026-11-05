package suspiciouspersonmap.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.service.LineWebhookSignatureVerifier;
import suspiciouspersonmap.service.ReportService;

@Slf4j
@RestController
@RequestMapping("/line")
@RequiredArgsConstructor
public class LineController {

    private static final String BAD_REQUEST_MESSAGE = "Bad Request";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";

    private final ReportService reportService;
    private final LineWebhookSignatureVerifier lineWebhookSignatureVerifier;
    private final ObjectMapper objectMapper;

    /**
     * LINE Webhook のイベントを受け取り、不審者情報の登録処理を実行する。
     *
     * @param body LINE Webhook のリクエスト本文
     * @param signature LINE Webhook の署名
     * @return Webhook の処理結果を表す HTTP レスポンス
     */
    @PostMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestBody String body,
            @RequestHeader(value = "X-Line-Signature", required = false) String signature) {

        log.info("=== LINE /line/callback HIT ===");

        // LINE Platform から送信された正当なリクエストかを確認し、不正な場合は後続処理を行わない。
        if (!lineWebhookSignatureVerifier.isValid(body, signature)) {
            log.warn("LINE Webhookの署名検証に失敗しました。stage=webhook_signature_validation");
            return ResponseEntity.badRequest().body(BAD_REQUEST_MESSAGE);
        }

        log.info("Body length: {}", body.length());

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.path("events");

            if (events.isArray() && events.size() > 0) {
                for (JsonNode event : events) {
                    String userId = event.path("source").path("userId").asText(null);
                    String text   = event.path("message").path("text").asText(null);
    
                    if (StringUtils.isNotEmpty(userId) && StringUtils.isNotEmpty(text)) {
                        Long eventTimestamp;
                        try {
                            eventTimestamp = extractEventTimestamp(event);
                        } catch (ReportException e) {
                            // Serviceへ到達しない入力エラーのため、Controllerで一度だけ記録する。
                            log.error("不審者情報の解析に失敗しました。stage={}, error={}, item={}, reason={}",
                                e.getStage().getLogValue(), e.getErrorType(), e.getItem(), e.getDetail());
                            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
                        }
                        reportService.processReportMessage(userId, text, eventTimestamp);
                    }
    
                    return ResponseEntity.ok("OK");
                }
            }

            return ResponseEntity.ok("None");

        } catch (ReportException e) {
            // ReportServiceで処理段階と例外を記録済みのため、ここでは重複出力しない。
            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
        } catch (JsonProcessingException e) {
            // Jacksonの診断には入力断片が含まれ得るため、本文や例外メッセージを記録しない。
            log.error("LINE WebhookのJSON解析に失敗しました。stage=webhook_json_parsing");
            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
        } catch (Exception e) {
            log.error("LINE Webhookのイベント処理に失敗しました。stage=webhook_event_processing", e);
            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }

    /**
     * LINEイベントからミリ秒単位のUnix時間を検証して取得する。
     * @param event LINE WebhookのイベントJSON
     * @return LINEイベント時刻
     * @throws ReportException イベント時刻が欠落、またはlongの範囲内の整数として取得できない場合
     */
    private Long extractEventTimestamp(JsonNode event) {
        JsonNode timestampNode = event.path("timestamp");
        if (timestampNode.isMissingNode() || timestampNode.isNull()) {
            throw new ReportException(
                ReportErrorEnum.INVALID_DATETIME,
                "eventTimestamp",
                "イベントタイムスタンプが指定されていません"
            );
        }
        if (!timestampNode.isIntegralNumber() || !timestampNode.canConvertToLong()) {
            throw new ReportException(
                ReportErrorEnum.INVALID_DATETIME,
                "eventTimestamp",
                "イベントタイムスタンプの型または範囲が不正です"
            );
        }
        return timestampNode.longValue();
    }
}
