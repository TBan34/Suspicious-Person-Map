package com.example.backend.controller;

import com.example.backend.service.ReportService;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.RequiredArgsConstructor;

@LineMessageHandler
@RequiredArgsConstructor
public class LineWebhookController {

    private final ReportService reportService;

    // 不審者情報メッセージを受信
    @EventMapping
    public void handleTextMessage(MessageEvent<TextMessageContent> event) {
        String userId = event.getSource().getUserId();
        String text = event.getMessage().getText();

        // 受信メッセージを解析・DB登録処理へ
        reportService.processReportMessage(userId, text);
    }

    // 位置情報メッセージ（LocationMessageContent）の実装は今回見送り
}
