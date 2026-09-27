package com.example.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineWebhookSignatureVerifierTest {

    private static final String OFFICIAL_EXAMPLE_BODY =
        "{\"destination\":\"U8e742f61d673b39c7fff3cecb7536ef0\",\"events\":[]}";
    private static final String OFFICIAL_EXAMPLE_CHANNEL_SECRET =
        "8c570fa6dd201bb328f1c1eac23a96d8";
    private static final String OFFICIAL_EXAMPLE_SIGNATURE =
        "GhRKmvmHys4Pi8DxkF4+EayaH0OqtJtaZxgTD9fMDLs=";

    /**
     * LINE 公式の検証例と同じ本文、チャネルシークレット、署名を受け入れることを確認する。
     */
    @Test
    void acceptsOfficialSignatureExample() {
        LineWebhookSignatureVerifier verifier = new LineWebhookSignatureVerifier(
            OFFICIAL_EXAMPLE_CHANNEL_SECRET
        );

        assertThat(verifier.isValid(OFFICIAL_EXAMPLE_BODY, OFFICIAL_EXAMPLE_SIGNATURE)).isTrue();
    }

    /**
     * 正常な署名を生成した後に本文を変更すると署名検証に失敗することを確認する。
     */
    @Test
    void rejectsModifiedBody() {
        LineWebhookSignatureVerifier verifier = new LineWebhookSignatureVerifier(
            OFFICIAL_EXAMPLE_CHANNEL_SECRET
        );

        assertThat(verifier.isValid(
            OFFICIAL_EXAMPLE_BODY + " ",
            OFFICIAL_EXAMPLE_SIGNATURE
        )).isFalse();
    }

    /**
     * 署名が指定されていない場合に署名検証に失敗することを確認する。
     */
    @Test
    void rejectsMissingSignature() {
        LineWebhookSignatureVerifier verifier = new LineWebhookSignatureVerifier(
            OFFICIAL_EXAMPLE_CHANNEL_SECRET
        );

        assertThat(verifier.isValid(OFFICIAL_EXAMPLE_BODY, null)).isFalse();
        assertThat(verifier.isValid(OFFICIAL_EXAMPLE_BODY, " ")).isFalse();
    }

    /**
     * Base64 として不正な署名を例外として外へ出さず拒否することを確認する。
     */
    @Test
    void rejectsMalformedSignature() {
        LineWebhookSignatureVerifier verifier = new LineWebhookSignatureVerifier(
            OFFICIAL_EXAMPLE_CHANNEL_SECRET
        );

        assertThat(verifier.isValid(OFFICIAL_EXAMPLE_BODY, "invalid-signature")).isFalse();
    }

    /**
     * 異なるチャネルシークレットで生成された署名を拒否することを確認する。
     */
    @Test
    void rejectsSignatureForDifferentChannelSecret() {
        LineWebhookSignatureVerifier verifier = new LineWebhookSignatureVerifier(
            "different-channel-secret"
        );

        assertThat(verifier.isValid(OFFICIAL_EXAMPLE_BODY, OFFICIAL_EXAMPLE_SIGNATURE)).isFalse();
    }

    /**
     * チャネルシークレットが空の場合に署名検証サービスを生成できないことを確認する。
     */
    @Test
    void rejectsBlankChannelSecret() {
        assertThatThrownBy(() -> new LineWebhookSignatureVerifier(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("LINEチャネルシークレットは必須です");
    }
}
