package com.example.backend.controller;

import com.example.backend.service.ReportService;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@LineMessageHandler
@RequiredArgsConstructor
public class LineWebhookController {

    private final ReportService reportService;

    // 不審者情報メッセージを受信
    @EventMapping
    public void handleTextMessage(MessageEvent<TextMessageContent> event) {
        try {
            String userId = event.getSource().getUserId();
            String text = event.getMessage().getText();

            log.info("Received text message from userId: {}, message length: {}", userId, text != null ? text.length() : 0);

            // 受信メッセージを解析・DB登録処理へ
            reportService.processReportMessage(userId, text);
            log.info("Successfully processed message from userId: {}", userId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid message received: {}", e.getMessage());
            // LINE Botにエラーメッセージを返す場合はここに実装
        } catch (Exception e) {
            log.error("Error processing message from userId: {}", 
            event.getSource() != null ? event.getSource().getUserId() : "unknown", e);
            // LINE Botにエラーメッセージを返す場合はここに実装
        }
    }

    // 位置情報メッセージ（LocationMessageContent）の実装は今回見送り
}
