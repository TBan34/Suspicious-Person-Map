package suspiciouspersonmap.service.parsing;

import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.model.ParsedReportMessage;

/** 日時・住所・概要・タグの各解析処理を順番に実行し、登録項目へまとめる。 */
@Component
@RequiredArgsConstructor
public class ReportMessageParser {
    private static final String DEFAULT_PREFECTURE = "福岡県";
    // 日時と住所の間にある半角・全角空白と読点。住所解析へ渡す前に先頭からだけ除去する。
    private static final Pattern DATE_TIME_DELIMITER_PATTERN = Pattern.compile("^[\\s　、]+");

    private final DateTimeParser dateTimeParser;
    private final ReportAddressExtractor addressExtractor;
    private final ReportTagClassifier tagClassifier;

    /**
     * 不審者情報の文章を解析する。
     * @param text 不審者情報メッセージ
     * @param eventTimestamp LINEイベント時刻（ミリ秒）
     * @return 検証済みの登録項目
     */
    public ParsedReportMessage parse(String text, Long eventTimestamp) {
        LocalDateTime japanDateTime = dateTimeParser.toJapanLocalDateTime(eventTimestamp);
        String normalizedMessage = ReportMessageTextUtils.normalizeForParsing(text);

        // 日時は正規化後の文章で判定し、終了位置を原文上の位置へ戻して原文表記を維持する。
        DateTimeParser.ParsedDateTime parsedDateTime = dateTimeParser.parse(normalizedMessage, japanDateTime);
        int originalDateTimeEndIndex = ReportMessageTextUtils.toOriginalIndex(
            text, parsedDateTime.dateTimeEndIndex());
        String messageAfterDateTime = DATE_TIME_DELIMITER_PATTERN
            .matcher(text.substring(originalDateTimeEndIndex))
            .replaceFirst("");

        // 住所解析が返す原文上の境界から概要を切り出し、概要だけをタグ判定へ渡す。
        ReportAddressExtractor.ParsedAddress parsedAddress = addressExtractor.extract(messageAfterDateTime);
        String summary = ReportMessageTextUtils.formatSummary(
            messageAfterDateTime.substring(parsedAddress.summaryStartIndex()));
        return new ParsedReportMessage(
            tagClassifier.classify(summary),
            parsedDateTime.occurDate(),
            DEFAULT_PREFECTURE,
            parsedAddress.municipality(),
            parsedAddress.district(),
            parsedAddress.addressDetails(),
            summary
        );
    }
}
