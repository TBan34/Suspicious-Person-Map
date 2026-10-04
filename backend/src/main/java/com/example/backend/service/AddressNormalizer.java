package com.example.backend.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Geocoding とデータベース保存に使用する住所構成要素の表記を正規化する。
 */
@Component
public class AddressNormalizer {

    // 数字に挟まれたダッシュ類と長音だけを対象とする（例: 1ー2 → 1-2、センターは維持）。
    private static final Pattern NUMERIC_SEPARATOR_PATTERN = Pattern.compile(
        "(?<=\\d)[‐‑‒–—―−ー](?=\\d)"
    );
    // 数字に挟まれた「番」「番地」だけを対象とする（例: 3番地4 → 3-4、一番町や3番館は維持）。
    private static final Pattern ADDRESS_NUMBER_WORD_SEPARATOR_PATTERN = Pattern.compile(
        "(?<=\\d)番地?(?=\\d)"
    );

    /**
     * 住所構成要素へ NFKC 正規化を適用し、空白と数字間の区切り文字を統一する。
     *
     * @param addressComponent 正規化対象の住所構成要素
     * @return 正規化した住所構成要素。入力が null または空白だけの場合は null
     */
    public String normalize(String addressComponent) {
        if (addressComponent == null) {
            return null;
        }

        String normalized = Normalizer.normalize(addressComponent, Normalizer.Form.NFKC);
        String withoutWhitespace = normalized.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

        if (withoutWhitespace.isEmpty()) {
            return null;
        }

        String withNormalizedNumericSeparators = NUMERIC_SEPARATOR_PATTERN
            .matcher(withoutWhitespace)
            .replaceAll("-");
        return ADDRESS_NUMBER_WORD_SEPARATOR_PATTERN
            .matcher(withNormalizedNumericSeparators)
            .replaceAll("-");
    }
}
