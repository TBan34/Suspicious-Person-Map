package suspiciouspersonmap.service.parsing;

import static suspiciouspersonmap.common.constant.ReportErrorEnum.INVALID_ADDRESS;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.INVALID_SUMMARY;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.service.AddressNormalizer;

/** 日時より後ろの文章から、福岡市内の住所と概要の開始位置を抽出する。 */
@Component
@RequiredArgsConstructor
public class ReportAddressExtractor {
    // メッセージ先頭の「福岡県」「福岡市」は省略可能だが、行政区は必須。
    // 先頭一致に限定し、「東京都福岡市」や「福岡南区」から途中一致で行政区を拾わない。
    private static final Pattern MUNICIPALITY_PATTERN = Pattern.compile(
        "^(?:福岡県)?(?:福岡市)?(?<ward>東区|博多区|中央区|南区|城南区|早良区|西区)"
    );
    // 「高宮1丁目11番」のように丁目がある住所。町域、丁目、任意の番地以降を分けて取得する。
    // 後続語まで先読みし、人物説明などを町域の続きとして取り込まない。
    private static final Pattern ADDRESS_WITH_CHOME_PATTERN = Pattern.compile(
        "^(?<town>[一-龠々ヶケぁ-んァ-ヶー]+?)"
        + "(?<chome>[0-9一二三四五六七八九十百]+丁目)"
        + "(?<details>[0-9一二三四五六七八九十百]+"
        + "(?:番地?|号|[-ー][0-9]+)(?:[0-9一二三四五六七八九十百]+号?)?)?"
        + "(?=付近|周辺|所在|の|に所在|にある|において|で|を|、|$)"
    );
    // 「天神」「梅光園団地」など、丁目がない町域を許容する住所。
    // 団地名に含まれる文字を場所導入部と取り違えないよう、「～団地」を先に照合する。
    private static final Pattern ADDRESS_WITH_OPTIONAL_CHOME_PATTERN = Pattern.compile(
        "^(?<town>[一-龠々ヶケぁ-んァ-ヶー]+?団地|[一-龠々ヶケぁ-んァ-ヶー]+?)"
        + "(?<chome>[0-9一二三四五六七八九十百]+丁目)?"
        + "(?<details>[0-9一二三四五六七八九十百]+"
        + "(?:番地?|号|[-ー][0-9]+)(?:[0-9一二三四五六七八九十百]+号?)?)?"
        + "(?=付近|周辺|所在|の|に所在|にある|において|で|を|、|$)"
    );
    // 住所直後の「付近の」「周辺にある」など、概要に残さない一般的な場所導入部。
    private static final Pattern LOCATION_PREFIX_PATTERN = Pattern.compile(
        "^(?:付近|周辺)?(?:に所在する|に所在|所在|にある)?(?:の)?"
    );
    // 固有性のない路上表現は概要から除外する。施設名や駅名は除外しない。
    private static final Pattern ROAD_PATTERN = Pattern.compile("^(?:路上|道路上|歩道上)(?:等)?");
    // 場所表現と事案内容をつなぐ「で、」「において」など。
    private static final Pattern CONNECTIVE_PATTERN = Pattern.compile("^(?:において|で|を)(?:、)?\\s*");
    // 住所後に残す施設・交通機関などの場所補足。後ろに接続表現がある場合だけ場所として扱う。
    // 例:「七隈小学校グラウンドで、」「走行中のバス車内で、」。
    private static final Pattern LOCATION_DETAIL_PATTERN = Pattern.compile(
        "^[^、。が]*?(?:公園|マンション|個人宅|駐車場|施設|ドラッグストア|広場|敷地|住宅|"
        + "エスカレーター|駅|橋|グラウンド|車内)[^、。]*?(?:において|で|に|を)(?:、)?"
    );
    // 抽出済みの丁目・番地内だけで漢数字を検出する。「五十川」「一番町」などの町域には適用しない。
    private static final Pattern KANJI_ADDRESS_NUMBER_PATTERN = Pattern.compile(
        "[一二三四五六七八九十百]+(?=丁目|番|号)"
    );

    private final AddressNormalizer addressNormalizer;

    /**
     * 抽出した住所構成要素と、原文上で概要が始まる位置。
     * @param municipality 市区町村
     * @param district 町域・丁目
     * @param addressDetails 番地以降。記載がなければnull
     * @param summaryStartIndex 日時除去後の原文上で概要が始まる位置
     */
    public record ParsedAddress(
        String municipality,
        String district,
        String addressDetails,
        int summaryStartIndex
    ) {}

    /**
     * 日時とその直後の区切りを除いた原文から、住所構成要素と概要の境界を抽出する。
     * @param messageAfterDateTime 日時とその直後の区切りを除いた原文
     * @return 住所構成要素と、原文上の概要開始位置
     */
    public ParsedAddress extract(String messageAfterDateTime) {
        String normalizedMessage = ReportMessageTextUtils.normalizeForParsing(messageAfterDateTime);
        Matcher municipalityMatcher = MUNICIPALITY_PATTERN.matcher(normalizedMessage);
        if (!municipalityMatcher.lookingAt()) {
            throw invalidAddress();
        }

        int addressStartIndex = municipalityMatcher.end();
        Matcher addressMatcher = findAddressMatcher(normalizedMessage.substring(addressStartIndex));
        String town = addressMatcher.group("town");
        if (isInvalidTown(town)) {
            throw invalidAddress();
        }

        int addressEndIndex = addressStartIndex + addressMatcher.end();
        int summaryStartIndex = findSummaryStartIndex(normalizedMessage, addressEndIndex);

        // HTML文字参照は、住所と概要のどちら側に存在するか確定してから該当項目の不正にする。
        ReportMessageTextUtils.validateNoHtmlCharacterReference(
            normalizedMessage.substring(0, addressEndIndex), INVALID_ADDRESS, "address");
        validateNoAdditionalAddress(normalizedMessage.substring(summaryStartIndex).stripLeading());

        String chome = addressMatcher.group("chome") == null
            ? ""
            : convertKanjiAddressNumbers(addressMatcher.group("chome"));
        String addressDetails = convertKanjiAddressNumbers(addressMatcher.group("details"));
        return new ParsedAddress(
            "福岡市" + municipalityMatcher.group("ward"),
            addressNormalizer.normalize(town + chome),
            addressNormalizer.normalize(addressDetails),
            ReportMessageTextUtils.toOriginalIndex(messageAfterDateTime, summaryStartIndex)
        );
    }

    /**
     * 行政区より後ろの文字列へ、丁目必須・丁目任意の順で住所パターンを適用する。
     * @param normalizedAddress 行政区より後ろの正規化済み文字列
     * @return 住所に一致したMatcher
     */
    private Matcher findAddressMatcher(String normalizedAddress) {
        Matcher addressMatcher = ADDRESS_WITH_CHOME_PATTERN.matcher(normalizedAddress);
        if (addressMatcher.lookingAt()) {
            return addressMatcher;
        }
        addressMatcher = ADDRESS_WITH_OPTIONAL_CHOME_PATTERN.matcher(normalizedAddress);
        if (addressMatcher.lookingAt()) {
            return addressMatcher;
        }
        throw invalidAddress();
    }

    /**
     * 抽出した町域が、駅・施設・一般的な場所表現を誤認した値でないか判定する。
     * @param town 抽出した町域候補
     * @return 町域として扱えない場合はtrue
     */
    private boolean isInvalidTown(String town) {
        return town.endsWith("駅")
            || town.contains("公園")
            || town.contains("マンション")
            || town.contains("施設")
            || town.equals("付近")
            || town.equals("周辺")
            || town.equals("路上");
    }

    /**
     * 住所より後ろの導入表現を確認し、概要が始まる位置を求める。
     * @param normalizedMessage 日時除去後の正規化済みメッセージ
     * @param addressEndIndex 住所が終了する位置
     * @return 正規化済みメッセージ上の概要開始位置
     */
    private int findSummaryStartIndex(String normalizedMessage, int addressEndIndex) {
        String messageAfterAddress = normalizedMessage.substring(addressEndIndex);
        Matcher prefixMatcher = LOCATION_PREFIX_PATTERN.matcher(messageAfterAddress);
        int summaryStartIndex = addressEndIndex;
        if (prefixMatcher.lookingAt()) {
            summaryStartIndex += prefixMatcher.end();
        }

        String summaryCandidate = normalizedMessage.substring(summaryStartIndex);
        Matcher roadMatcher = ROAD_PATTERN.matcher(summaryCandidate);
        if (roadMatcher.lookingAt()) {
            summaryStartIndex += roadMatcher.end();
            summaryCandidate = normalizedMessage.substring(summaryStartIndex);
        }

        Matcher connectiveMatcher = CONNECTIVE_PATTERN.matcher(summaryCandidate);
        if (connectiveMatcher.lookingAt()) {
            return summaryStartIndex + connectiveMatcher.end();
        }
        if (summaryCandidate.startsWith("、")) {
            return summaryStartIndex + 1;
        }
        if (StringUtils.isBlank(summaryCandidate)) {
            throw new ReportException(INVALID_SUMMARY, "summary", "概要が指定されていません");
        }

        Matcher locationDetailMatcher = LOCATION_DETAIL_PATTERN.matcher(summaryCandidate);
        if (!locationDetailMatcher.lookingAt()) {
            throw invalidAddress();
        }
        if (StringUtils.isBlank(summaryCandidate.substring(locationDetailMatcher.end()))) {
            throw new ReportException(INVALID_SUMMARY, "summary", "事案内容が指定されていません");
        }
        // 施設名等は概要に残すため、場所補足の開始位置をそのまま返す。
        return summaryStartIndex;
    }

    /**
     * 概要先頭に別住所が残っていないことを検証する。レスポンス: なし。
     * @param normalizedSummary 正規化済みの概要候補
     */
    private void validateNoAdditionalAddress(String normalizedSummary) {
        if (MUNICIPALITY_PATTERN.matcher(normalizedSummary).lookingAt()
                || ADDRESS_WITH_CHOME_PATTERN.matcher(normalizedSummary).lookingAt()) {
            // 読点で列挙された2件目の住所を概要として登録しない。
            throw invalidAddress();
        }
    }

    /**
     * 抽出済みの丁目・番地に含まれる漢数字を算用数字へ変換する。
     * @param addressComponent 丁目または番地。記載がなければnull
     * @return 数値変換済み文字列。記載がなければnull
     */
    private String convertKanjiAddressNumbers(String addressComponent) {
        if (addressComponent == null) {
            return null;
        }
        return KANJI_ADDRESS_NUMBER_PATTERN.matcher(addressComponent).replaceAll(matchResult -> {
            int total = 0;
            int digit = 0;
            for (char character : matchResult.group().toCharArray()) {
                int unit = character == '百' ? 100 : character == '十' ? 10 : 0;
                if (unit > 0) {
                    total += (digit == 0 ? 1 : digit) * unit;
                    digit = 0;
                } else {
                    digit = "一二三四五六七八九".indexOf(character) + 1;
                }
            }
            return Integer.toString(total + digit);
        });
    }

    /**
     * 入力値を含まない住所不正例外を生成する。
     * @return 住所不正例外
     */
    private ReportException invalidAddress() {
        return new ReportException(INVALID_ADDRESS, "address", "住所または概要との境界が不正です");
    }
}
