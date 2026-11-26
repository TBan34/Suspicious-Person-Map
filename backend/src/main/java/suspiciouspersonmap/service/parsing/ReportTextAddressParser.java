package suspiciouspersonmap.service.parsing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.service.AddressNormalizer;

/** 日時より後ろの文章を解析し、福岡市内の住所と概要の開始位置を求める。 */
@Component
@RequiredArgsConstructor
public class ReportTextAddressParser {
    // テキスト先頭の「福岡県」「福岡市」は省略可能だが、行政区は必須。
    // 先頭一致に限定し、「東京都福岡市」や「福岡南区」から途中一致で行政区を拾わない。
    private static final Pattern MUNICIPALITY_PATTERN = Pattern.compile(
        "^(?:福岡県)?(?:福岡市)?(?<ward>東区|博多区|中央区|南区|城南区|早良区|西区)"
    );
    // 「高宮1丁目11番」のように丁目がある住所。町域、丁目、任意の番地・号を分けて取得する。
    // 後続語まで先読みし、人物説明などを町域の続きとして取り込まない。
    private static final Pattern ADDRESS_WITH_CHOME_PATTERN = Pattern.compile(
        "^(?<town>[一-龠々ヶケぁ-んァ-ヶー]+?)"
        + "(?<chome>[0-9一二三四五六七八九十百]+丁目)"
        + "(?<blockAndHouseNumber>[0-9一二三四五六七八九十百]+"
        + "(?:番地?|号|[-ー][0-9]+)(?:[0-9一二三四五六七八九十百]+号?)?)?"
        + "(?=付近|周辺|所在|の|に所在|にある|において|で|を|、|$)"
    );
    // 「天神」「梅光園団地」など、丁目がない町域を許容する住所。
    // 団地名に含まれる文字を場所導入部と取り違えないよう、「～団地」を先に照合する。
    private static final Pattern ADDRESS_WITH_OPTIONAL_CHOME_PATTERN = Pattern.compile(
        "^(?<town>[一-龠々ヶケぁ-んァ-ヶー]+?団地|[一-龠々ヶケぁ-んァ-ヶー]+?)"
        + "(?<chome>[0-9一二三四五六七八九十百]+丁目)?"
        + "(?<blockAndHouseNumber>[0-9一二三四五六七八九十百]+"
        + "(?:番地?|号|[-ー][0-9]+)(?:[0-9一二三四五六七八九十百]+号?)?)?"
        + "(?=付近|周辺|所在|の|に所在|にある|において|で|を|、|$)"
    );
    // 住所直後の「付近の」「周辺にある」など、概要に残さない一般的な場所導入部。
    private static final Pattern LOCATION_PREFIX_PATTERN = Pattern.compile(
        "^(?:付近|周辺)?(?:に所在する|に所在|所在|にある)?(?:の)?"
    );
    // 固有性のない路上表現は概要から除外する。施設名や駅名は除外しない。
    private static final Pattern ROAD_PATTERN = Pattern.compile("^(?:路上|道路上|歩道上)(?:等)?");
    // 場所表現と概要をつなぐ「で、」「において」など。
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
     * 住所の解析結果。住所構成要素と、原文上で概要が始まる位置を保持する。
     * @param municipality 市区町村
     * @param townArea 町域・丁目
     * @param blockAndHouseNumber 番地・号。記載がなければnull
     * @param descriptionStartIndex 日時除去後の原文上で概要が始まる位置
     */
    public record Result(
        String municipality,
        String townArea,
        String blockAndHouseNumber,
        int descriptionStartIndex
    ) {}

    /**
     * 日時とその直後の区切りを除いた原文を解析し、住所構成要素と概要の境界を求める。
     * @param textAfterDateTime 日時とその直後の区切りを除いた原文
     * @return 住所構成要素と、原文上の概要開始位置
     */
    public Result parse(String textAfterDateTime) {
        // 全角英数字や半角カナの表記をそろえ、福岡市の行政区で始まる住所か確認する。
        String normalizedText = ReportTextUtils.normalize(textAfterDateTime);
        Matcher municipalityMatcher = MUNICIPALITY_PATTERN.matcher(normalizedText);
        if (!municipalityMatcher.lookingAt()) {
            throw invalidAddress();
        }

        // 行政区に続く町域・丁目・番地を読み取り、施設名などを町域と誤認していないか確認する。
        int addressStartIndex = municipalityMatcher.end();
        Matcher addressMatcher = findAddressMatcher(normalizedText.substring(addressStartIndex));
        String town = addressMatcher.group("town");
        if (isInvalidTown(town)) {
            throw invalidAddress();
        }

        // 住所に続く「付近の路上で」などの表現を読み飛ばし、概要が始まる箇所を探す。
        int addressEndIndex = addressStartIndex + addressMatcher.end();
        int descriptionStartIndex = findDescriptionStartIndex(normalizedText, addressEndIndex);

        // 住所にHTML文字参照が含まれていないか確認し、含まれていれば住所の入力エラーにする。
        ReportTextUtils.validateNoHtmlCharacterReference(
            normalizedText.substring(0, addressEndIndex), ReportErrorEnum.INVALID_ADDRESS, "address");

        // 複数の住所を1件として登録しないよう、概要の先頭に別の住所が残っていないか確認する。
        validateNoAdditionalAddress(normalizedText.substring(descriptionStartIndex).stripLeading());

        // 地名の漢数字は残し、丁目・番地に含まれる漢数字だけを算用数字へ変換する。
        String chome = addressMatcher.group("chome") == null
            ? ""
            : convertKanjiAddressNumbers(addressMatcher.group("chome"));
        String blockAndHouseNumber = convertKanjiAddressNumbers(addressMatcher.group("blockAndHouseNumber"));

        // 住所の表記を統一し、原文で概要が始まる位置とともに返す。
        return new Result(
            "福岡市" + municipalityMatcher.group("ward"),
            addressNormalizer.normalize(town + chome),
            addressNormalizer.normalize(blockAndHouseNumber),
            ReportTextUtils.findOriginalIndex(textAfterDateTime, descriptionStartIndex)
        );
    }

    /**
     * 行政区より後ろの文字列へ、丁目必須・丁目任意の順で住所パターンを適用する。
     * @param normalizedAddress 行政区より後ろの正規化済み文字列
     * @return 住所に一致したMatcher
     */
    private Matcher findAddressMatcher(String normalizedAddress) {
        // 丁目を含む住所として読めるか、先に確認する。
        Matcher addressMatcher = ADDRESS_WITH_CHOME_PATTERN.matcher(normalizedAddress);
        if (addressMatcher.lookingAt()) {
            return addressMatcher;
        }

        // 一致しなければ、丁目が省略されていてもよい形式で確認する。
        addressMatcher = ADDRESS_WITH_OPTIONAL_CHOME_PATTERN.matcher(normalizedAddress);
        if (addressMatcher.lookingAt()) {
            return addressMatcher;
        }

        // どちらの形式にも一致しない文章は、住所として受け付けない。
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
     * @param normalizedText 日時除去後の正規化済みテキスト
     * @param addressEndIndex 住所が終了する位置
     * @return 正規化済みテキスト上の概要開始位置
     */
    private int findDescriptionStartIndex(String normalizedText, int addressEndIndex) {
        // 概要に残さない「付近の」「周辺にある」などを読み飛ばす。
        String textAfterAddress = normalizedText.substring(addressEndIndex);
        Matcher prefixMatcher = LOCATION_PREFIX_PATTERN.matcher(textAfterAddress);
        int descriptionStartIndex = addressEndIndex;
        if (prefixMatcher.lookingAt()) {
            descriptionStartIndex += prefixMatcher.end();
        }

        // 「路上」「歩道上」などの一般的な場所表現も概要から除く。
        String descriptionCandidate = normalizedText.substring(descriptionStartIndex);
        Matcher roadMatcher = ROAD_PATTERN.matcher(descriptionCandidate);
        if (roadMatcher.lookingAt()) {
            descriptionStartIndex += roadMatcher.end();
            descriptionCandidate = normalizedText.substring(descriptionStartIndex);
        }

        // 「で、」「において」などがあれば、その直後を概要の開始位置にする。
        Matcher connectiveMatcher = CONNECTIVE_PATTERN.matcher(descriptionCandidate);
        if (connectiveMatcher.lookingAt()) {
            return descriptionStartIndex + connectiveMatcher.end();
        }

        // 接続表現がなく読点だけがある場合も、その直後から概要を取り出す。
        if (descriptionCandidate.startsWith("、")) {
            return descriptionStartIndex + 1;
        }

        // 場所表現を除いた後に文章が残っていなければ、概要の入力エラーにする。
        if (StringUtils.isBlank(descriptionCandidate)) {
            throw new ReportException(ReportErrorEnum.INVALID_DESCRIPTION, "description", "概要が指定されていません");
        }

        // 施設名などを概要に残す場合は、その後に「で」などの接続表現があることを確認する。
        Matcher locationDetailMatcher = LOCATION_DETAIL_PATTERN.matcher(descriptionCandidate);
        if (!locationDetailMatcher.lookingAt()) {
            throw invalidAddress();
        }

        // 場所の説明だけで本文がない場合は、概要の入力エラーにする。
        if (StringUtils.isBlank(descriptionCandidate.substring(locationDetailMatcher.end()))) {
            throw new ReportException(ReportErrorEnum.INVALID_DESCRIPTION, "description", "概要が指定されていません");
        }

        // 施設名なども保存できるよう、接続表現の後ではなく、場所の説明が始まる位置を返す。
        return descriptionStartIndex;
    }

    /**
     * 概要先頭に別住所が残っていないことを検証する。レスポンス: なし。
     * @param normalizedDescription 正規化済みの概要候補
     */
    private void validateNoAdditionalAddress(String normalizedDescription) {
        if (MUNICIPALITY_PATTERN.matcher(normalizedDescription).lookingAt()
                || ADDRESS_WITH_CHOME_PATTERN.matcher(normalizedDescription).lookingAt()) {
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
        // 番地・号が省略されている場合は、未指定のまま返す。
        if (addressComponent == null) {
            return null;
        }

        // 「丁目」「番」「号」の直前にある漢数字を、まとまりごとに数値へ変換する。
        return KANJI_ADDRESS_NUMBER_PATTERN.matcher(addressComponent).replaceAll(matchResult -> {
            // 左から順に読み、一～九の数字と十・百の位を組み合わせて合計する。
            int total = 0;
            int digit = 0;
            for (char character : matchResult.group().toCharArray()) {
                int unit = character == '百' ? 100 : character == '十' ? 10 : 0;
                if (unit > 0) {
                    // 「二十」は2×10、「十」は1×10として加算し、次の数字を読む準備をする。
                    total += (digit == 0 ? 1 : digit) * unit;
                    digit = 0;
                } else {
                    // 一～九は、次に現れる十・百の係数、または最後の一の位として保持する。
                    digit = "一二三四五六七八九".indexOf(character) + 1;
                }
            }
            // 最後に残った一の位を加え、算用数字の文字列にする。
            return Integer.toString(total + digit);
        });
    }

    /**
     * 入力値を含まない住所不正例外を生成する。
     * @return 住所不正例外
     */
    private ReportException invalidAddress() {
        return new ReportException(
            ReportErrorEnum.INVALID_ADDRESS,
            "address",
            "住所または概要との境界が不正です"
        );
    }
}
