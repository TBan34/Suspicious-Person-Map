package suspiciouspersonmap.service.parsing;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;

/** 不審者情報メッセージの解析で共通して使用する文字列処理。 */
final class ReportMessageTextUtils {
    // 「&amp;」「&#12354;」「&#x3042;」形式のHTML文字参照。入力値はデコードせず不正として扱う。
    private static final Pattern HTML_CHARACTER_REFERENCE_PATTERN = Pattern.compile(
        "&(?:[A-Za-z][A-Za-z0-9]*|#[0-9]+|#[xX][0-9a-fA-F]+);"
    );
    // 改行とその前後の水平空白。概要中の段落だけを単一の半角空白へ置き換える。
    private static final Pattern PARAGRAPH_BREAK_PATTERN = Pattern.compile("[\\h]*\\R[\\h\\r\\n]*");

    /** インスタンス化を禁止する。 */
    private ReportMessageTextUtils() {}

    /**
     * 正規表現による判定用に、全角英数字や半角カナなどの表記を統一する。
     * 保存する概要にはこの戻り値を使用せず、原文表記を維持する。
     * @param text 不審者情報メッセージまたはその一部
     * @return NFKC正規化した判定用文字列
     */
    static String normalizeForParsing(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC);
    }

    /**
     * HTML文字参照が含まれていないことを検証する。レスポンス: なし。
     * @param text 検証対象文字列
     * @param errorType 検出時の解析エラー種別
     * @param item 検出時のエラー項目
     */
    static void validateNoHtmlCharacterReference(
            String text,
            ReportErrorEnum errorType,
            String item) {
        if (text != null && HTML_CHARACTER_REFERENCE_PATTERN.matcher(text).find()) {
            throw new ReportException(errorType, item, "HTML文字参照が含まれています");
        }
    }

    /**
     * NFKC正規化後の文字位置を、正規化前の原文上の文字位置へ変換する。
     * 半角カナ＋濁点など、正規化によって文字数が変わる入力でも原文を正しい位置で切り出す。
     * @param originalText 正規化前の原文
     * @param normalizedIndex 正規化後の文字列上の位置
     * @return 正規化後の位置に対応する原文上の位置
     */
    static int toOriginalIndex(String originalText, int normalizedIndex) {
        String normalizedText = normalizeForParsing(originalText);
        if (normalizedIndex < 0 || normalizedIndex > normalizedText.length()) {
            throw invalidSummaryBoundary();
        }
        String expectedNormalizedPrefix = normalizedText.substring(0, normalizedIndex);
        for (int originalIndex = 0; originalIndex <= originalText.length(); originalIndex++) {
            String normalizedPrefix = normalizeForParsing(originalText.substring(0, originalIndex));
            // 文字数だけでは「ﾊ」と「ﾊﾟ」の正規化後がともに1文字となる位置を区別できない。
            if (normalizedPrefix.equals(expectedNormalizedPrefix)) {
                return originalIndex;
            }
        }
        throw invalidSummaryBoundary();
    }

    /**
     * 概要の切り出し位置を特定できない場合の例外を生成する。
     * @return 概要境界不正例外
     */
    private static ReportException invalidSummaryBoundary() {
        return new ReportException(ReportErrorEnum.INVALID_SUMMARY, "summary", "概要の境界が不明です");
    }

    /**
     * 概要の原文表記を維持したまま、前後の空白と段落区切りだけを整形する。
     * @param summaryText 住所より後ろの概要原文
     * @return 検証・整形済みの概要
     */
    static String formatSummary(String summaryText) {
        validateNoHtmlCharacterReference(summaryText, ReportErrorEnum.INVALID_SUMMARY, "summary");
        String formattedSummary = PARAGRAPH_BREAK_PATTERN.matcher(summaryText.strip()).replaceAll(" ");
        if (StringUtils.isBlank(formattedSummary)) {
            throw new ReportException(ReportErrorEnum.INVALID_SUMMARY, "summary", "概要が指定されていません");
        }
        return formattedSummary;
    }
}
