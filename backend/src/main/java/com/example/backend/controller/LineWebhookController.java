package com.example.backend.controller;

import com.example.backend.service.ReportService;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LINE Webhookコントローラー
 */
@Slf4j
@LineMessageHandler
@RequiredArgsConstructor
public class LineWebhookController {

    private final ReportService reportService;

    /*
     * LINEのMessaging APIにて受信した不審者情報メッセージの登録処理を呼び出す。
     * text: 不審者情報メッセージ
     * item: 抽出対象項目
     */
    @EventMapping
    public void handleTextMessage(MessageEvent<TextMessageContent> event) {
        try {
            // ユーザーIDと不審者情報メッセージを取得
            String userId = event.getSource().getUserId();
            String text = event.getMessage().getText();
            log.info("Received text message from userId: {}, text: {}", userId, text);

            // 不審者情報を登録
            reportService.processReportMessage(userId, text);
            log.info("Successfully processed message from userId: {}", userId);

        // 不正なメッセージを取得した場合、エラー
        } catch (IllegalArgumentException e) {
            log.warn("Invalid message received: {}", e.getMessage());

            // NOTE: 今後検討
            // バリデーションエラーの場合、ユーザーに分かりやすいメッセージを送信
            //  try {
            //     lineMessagingClient.replyMessage(
            //         new ReplyMessage(event.getReplyToken(),
            //             new TextMessage("入力内容に問題があります。\n" + 
            //                           "以下の形式で入力してください：\n\n" +
            //                           "都道府県: 東京都\n" +
            //                           "市区町村: 渋谷区\n" +
            //                           "不審者情報: 詳細情報"))
            //     ).get();
            // } catch (Exception sendException) {
            //     log.error("Failed to send error message to user", sendException);
            // }

        // 上記以外のエラー
        } catch (Exception e) {
            log.error("Error processing message from userId: {}", 
            event.getSource() != null ? event.getSource().getUserId() : "unknown", e);
            
            // NOTE: 今後検討
            // システムエラーの場合、汎用的なエラーメッセージを送信
            // try {
            //     lineMessagingClient.replyMessage(
            //         new ReplyMessage(event.getReplyToken(),
            //             new TextMessage("申し訳ございません。システムエラーが発生しました。\n" +
            //                           "しばらく時間をおいて再度お試しください。"))
            //     ).get();
            // } catch (Exception sendException) {
            //     log.error("Failed to send error message to user", sendException);
            // }
        }
    }

    // 位置情報メッセージ（LocationMessageContent）の実装は今後検討
}
