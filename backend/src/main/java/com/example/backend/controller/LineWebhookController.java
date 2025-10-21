package com.example.lineapp.controller;

import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import org.springframework.web.bind.annotation.RestController;

@LineMessageHandler
public class LineWebhookController {

    private final ReportService reportService;

    public LineWebhookController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 不審者情報メッセージを受信
    @EventMapping
    public void handleTextMessage(MessageEvent<TextMessageContent> event) {
        String text = event.getMessage().getText();
        String userId = event.getSource().getUserId();

        // 受信メッセージを解析・DB登録処理へ
        reportService.processReportMessage(userId, text);
    }
}
