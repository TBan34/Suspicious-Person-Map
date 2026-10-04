package com.example.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressNormalizerTest {

    private final AddressNormalizer addressNormalizer = new AddressNormalizer();

    /**
     * 全角英数字、半角カナ、Unicode空白、数字間のハイフン類が統一されることを確認する。
     */
    @Test
    void normalizesRepresentativeAddressVariations() {
        String normalized = addressNormalizer.normalize("　ﾃﾝｼﾞﾝ ３丁目\t１５ー１　");

        assertThat(normalized).isEqualTo("テンジン3丁目15-1");
    }

    /**
     * 住所に含まれる全角数字の0から9が、すべて半角数字へ統一されることを確認する。
     */
    @Test
    void normalizesAllFullWidthDigits() {
        String normalized = addressNormalizer.normalize("０１２３４５６７８９");

        assertThat(normalized).isEqualTo("0123456789");
    }

    /**
     * 数字に挟まれた番と番地が、半角ハイフンへ統一されることを確認する。
     */
    @Test
    void normalizesJapaneseAddressNumberSeparators() {
        assertThat(addressNormalizer.normalize("３番４")).isEqualTo("3-4");
        assertThat(addressNormalizer.normalize("３番地４")).isEqualTo("3-4");
    }

    /**
     * 数字に挟まれていないカタカナの長音と丁目表記を過剰に変換しないことを確認する。
     */
    @Test
    void preservesJapaneseAddressNotation() {
        String normalized = addressNormalizer.normalize("福岡センター１丁目");

        assertThat(normalized).isEqualTo("福岡センター1丁目");
        assertThat(addressNormalizer.normalize("仙台市青葉区一番町４丁目"))
            .isEqualTo("仙台市青葉区一番町4丁目");
        assertThat(addressNormalizer.normalize("３番館４号室"))
            .isEqualTo("3番館4号室");
    }

    /**
     * nullまたは空白だけの入力が null へ正規化されることを確認する。
     */
    @Test
    void returnsNullForMissingValue() {
        assertThat(addressNormalizer.normalize(null)).isNull();
        assertThat(addressNormalizer.normalize(" \t　")).isNull();
    }

    /**
     * 正規化済みの住所を再度正規化しても結果が変化しないことを確認する。
     */
    @Test
    void isIdempotent() {
        String normalized = addressNormalizer.normalize("天神 １丁目１５－１");

        assertThat(addressNormalizer.normalize(normalized)).isEqualTo(normalized);
    }
}
