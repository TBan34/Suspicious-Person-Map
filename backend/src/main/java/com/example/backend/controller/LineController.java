package com.example.backend.controller;

import com.example.backend.service.ReportService;
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

    private final ReportService reportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${line.bot.channelSecret}")
    private String channelSecret;

    @PostMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestBody String body,
            @RequestHeader(value = "X-Line-Signature", required = false) String signature) {

        log.info("=== LINE /line/callback HIT ===");

        // 署名検証スキップ（テスト用）
        log.info("Body length: {}", body.length());
        log.info("Signature: {}", signature);

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
                        log.info("ReportService called successfully");
                    }
    
                    return ResponseEntity.ok("OK");
                }
            }

            return ResponseEntity.ok("None");

        } catch (Exception e) {
            log.error("Webhook error", e);
            return ResponseEntity.status(500).body("Internal Error: " + e.getMessage());
        }
    }
}
