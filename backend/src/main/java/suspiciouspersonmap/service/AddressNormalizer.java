package suspiciouspersonmap.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Geocoding とデータベース保存に使用する住所構成要素の表記を正規化する。
 */
@Component
public class AddressNormalizer {

    // 数字に挟まれたダッシュ類と長音記号を、ハイフンへの置換対象として検出する。単語内の長音記号は対象外。
    private static final Pattern REPLACE_DASH_PATTERN = Pattern.compile(
        "(?<=\\d)[‐‑‒–—―−ー](?=\\d)"
    );
    // 数字に挟まれた「番」「番地」を、ハイフンへの置換対象として検出する。一番町や3番館は対象外。
    private static final Pattern REPLACE_BANCHI_PATTERN = Pattern.compile(
        "(?<=\\d)番地?(?=\\d)"
    );

    /**
     * 住所構成要素へ NFKC 正規化を適用し、空白と数字間の区切り文字を統一する。
     *
     * @param addressComponent 正規化対象の住所構成要素
     * @return 正規化した住所構成要素。入力が null または空白だけの場合は null
     */
    public String normalize(String addressComponent) {
        if (StringUtils.isEmpty(addressComponent)) {
            return null;
        }

        // 文字種をNFKC形式へ統一し、住所内の空白文字を除去する。
        String normalized = Normalizer.normalize(addressComponent, Normalizer.Form.NFKC);
        String withoutWhitespace = normalized.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

        // 正規化後に文字が残らない場合は、住所なしとして扱う。
        if (StringUtils.isEmpty(withoutWhitespace)) {
            return null;
        }

        // 数字に挟まれたダッシュ類と長音記号をハイフンへ統一する。
        String addressWithNormalizedDash = REPLACE_DASH_PATTERN
            .matcher(withoutWhitespace)
            .replaceAll("-");

        // 数字に挟まれた「番」「番地」をハイフンへ置換する。
        String normalizedAddress = REPLACE_BANCHI_PATTERN
            .matcher(addressWithNormalizedDash)
            .replaceAll("-");

        return normalizedAddress;
    }
}
