package com.example.backend.service;

import com.linecorp.bot.parser.LineSignatureValidator;
import com.linecorp.bot.parser.SignatureValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * LINE Webhook の署名を検証する。
 */
@Service
public class LineWebhookSignatureVerifier {

    private final SignatureValidator signatureValidator;

    /**
     * チャネルシークレットを使用する LINE Webhook 署名検証サービスを生成する。
     *
     * @param channelSecret LINE Messaging API のチャネルシークレット
     */
    public LineWebhookSignatureVerifier(
            @Value("${line.bot.channelSecret}") String channelSecret) {
        if (channelSecret == null || channelSecret.isBlank()) {
            throw new IllegalArgumentException("LINEチャネルシークレットは必須です");
        }

        this.signatureValidator = new LineSignatureValidator(
            channelSecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 受信した本文と X-Line-Signature ヘッダーの組み合わせが正しいことを検証する。
     *
     * @param body LINE Webhook の未解析リクエスト本文
     * @param signature X-Line-Signature ヘッダーの値
     * @return 署名が存在して本文と一致する場合は true、それ以外は false
     */
    public boolean isValid(String body, String signature) {
        if (body == null || signature == null || signature.isBlank()) {
            return false;
        }

        try {
            return signatureValidator.validateSignature(
                body.getBytes(StandardCharsets.UTF_8),
                signature
            );
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
