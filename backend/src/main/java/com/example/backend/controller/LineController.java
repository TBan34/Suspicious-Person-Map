package com.example.backend.controller;

import com.example.backend.exception.ReportProcessingException;
import com.example.backend.service.ReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Slf4j
@RestController
@RequestMapping("/line")
@RequiredArgsConstructor
public class LineController {

    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";

    private final ReportService reportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${line.bot.channelSecret}")
    private String channelSecret;

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

        // 署名検証スキップ（テスト用）
        log.info("Body length: {}", body.length());

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.path("events");

            if (events.isArray() && events.size() > 0) {
                for (JsonNode event : events) {
                    String userId = event.path("source").path("userId").asText(null);
                    String text   = event.path("message").path("text").asText(null);
    
                    if (StringUtils.isNotEmpty(userId) && StringUtils.isNotEmpty(text)) {
                        // ReportService呼び出し
                        reportService.processReportMessage(userId, text);
                    }
    
                    return ResponseEntity.ok("OK");
                }
            }

            return ResponseEntity.ok("None");

        } catch (ReportProcessingException e) {
            // ReportServiceで処理段階と例外を記録済みのため、ここでは重複出力しない。
            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
        } catch (JsonProcessingException e) {
            log.error("LINE WebhookのJSON解析に失敗しました。stage=webhook_json_parsing", e);
            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
        } catch (Exception e) {
            log.error("LINE Webhookのイベント処理に失敗しました。stage=webhook_event_processing", e);
            return ResponseEntity.status(500).body(INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }
}
