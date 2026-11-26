package suspiciouspersonmap.service.parsing;

import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.model.ParsedReportText;

/** 日時・住所・概要・タグの各解析処理を順番に実行し、登録項目へまとめる。 */
@Component
@RequiredArgsConstructor
public class ReportTextParser {
    private static final String DEFAULT_PREFECTURE = "福岡県";
    // 日時と住所の間にある半角・全角空白と読点。住所解析へ渡す前に先頭からだけ除去する。
    private static final Pattern DATE_TIME_DELIMITER_PATTERN = Pattern.compile("^[\\s　、]+");

    private final ReportTextDateTimeParser dateTimeParser;
    private final ReportTextAddressParser addressParser;
    private final ReportTagClassifier tagClassifier;

    /**
     * 不審者情報の文章を解析する。
     * @param text 不審者情報テキスト
     * @param eventTimestamp LINEイベント時刻（ミリ秒）
     * @return 検証済みの登録項目
     */
    public ParsedReportText parse(String text, Long eventTimestamp) {
        LocalDateTime japanDateTime = dateTimeParser.toJapanLocalDateTime(eventTimestamp);
        String normalizedText = ReportTextUtils.normalize(text);

        // 概要の表記を変えずに保存するため、日時より後ろの文章は原文から切り出す。
        // 正規化で文字数が変わることがあるため、原文で日時が終わる箇所を探し、その後の文章を取り出す。
        ReportTextDateTimeParser.Result parsedDateTime = dateTimeParser.parse(normalizedText, japanDateTime);
        int originalDateTimeEndIndex = ReportTextUtils.findOriginalIndex(
            text, parsedDateTime.dateTimeEndIndex());
        String textAfterDateTime = DATE_TIME_DELIMITER_PATTERN
            .matcher(text.substring(originalDateTimeEndIndex))
            .replaceFirst("");

        // 日時や住所の語句でタグを誤判定しないよう、タグ判定には概要だけを使う。
        // 住所解析で求めた概要の開始位置から原文を切り出し、空白と改行を整えて保存用の概要にする。
        ReportTextAddressParser.Result parsedAddress = addressParser.parse(textAfterDateTime);
        String description = ReportTextUtils.formatDescription(
            textAfterDateTime.substring(parsedAddress.descriptionStartIndex()));
        return new ParsedReportText(
            tagClassifier.classify(description),
            parsedDateTime.occurDatetime(),
            DEFAULT_PREFECTURE,
            parsedAddress.municipality(),
            parsedAddress.townArea(),
            parsedAddress.blockAndHouseNumber(),
            description
        );
    }
}
