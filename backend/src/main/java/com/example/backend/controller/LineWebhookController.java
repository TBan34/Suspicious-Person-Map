package com.example.lineapp.controller;

import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import org.springframework.web.bind.annotation.RestController;

@LineMessageHandler
public class LineWebhookController {

    // 位置情報メッセージを受信
    @EventMapping
    public void handleLocationMessage(MessageEvent<LocationMessageContent> event) {
        LocationMessageContent msg = event.getMessage();
        String title = msg.getTitle();
        String address = msg.getAddress();
        double lat = msg.getLatitude();
        double lon = msg.getLongitude();

        System.out.println("位置情報受信:");
        System.out.println("タイトル: " + title);
        System.out.println("住所: " + address);
        System.out.println("緯度: " + lat + ", 経度: " + lon);

        // TODO: DBに保存
    }

    // テキストメッセージを受信（住所など）
    @EventMapping
    public void handleTextMessage(MessageEvent<TextMessageContent> event) {
        String text = event.getMessage().getText();
        System.out.println("テキスト受信: " + text);

        // TODO: Google Geocoding APIで住所→緯度経度に変換
    }
}
