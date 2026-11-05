package suspiciouspersonmap.service.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.model.ParsedReportMessage;
import suspiciouspersonmap.service.ReportParserTestSupport;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 実サンプルにない日時・住所・概要の境界条件を検証する。 */
class ReportMessageParserTest {
    private static final long EVENT_TIMESTAMP = OffsetDateTime
        .parse("2026-10-25T10:00:00+09:00").toInstant().toEpochMilli();
    private final ReportMessageParser parser = ReportParserTestSupport.createParser();

    /**
     * 時半・和暦・全角・午前午後・実在するうるう日を厳密に変換することを確認する。
     */
    @ParameterizedTest
    @CsvSource({
        "2026年1月4日午後1時半,2026-01-04T13:30",
        "令和8年1月4日午後0時,2026-01-04T12:00",
        "令和元年5月1日午前0時,2019-05-01T00:00",
        "２０２６年１月４日午前８時３０分頃,2026-01-04T08:30",
        "2024年2月29日午後9時ころ,2024-02-29T21:00",
        "1月4日午後9時30分,2026-01-04T21:30"
    })
    void parsesStrictDateTimes(String dateTimeText, String expectedDateTime) {
        assertThat(parser.parse(createReportMessage(dateTimeText), EVENT_TIMESTAMP).occurDate())
            .isEqualTo(LocalDateTime.parse(expectedDateTime));
    }

    /**
     * 省略年は直近の実在する過去年月日を選び、実行日の時計や端末タイムゾーンに依存しないことを確認する。
     */
    @Test
    void infersClosestPastCalendarDate() {
        long march = OffsetDateTime.parse("2026-03-01T12:00:00+09:00").toInstant().toEpochMilli();
        long january = OffsetDateTime.parse("2027-01-05T12:00:00+09:00").toInstant().toEpochMilli();
        assertThat(parser.parse(createReportMessage("2月29日午後9時"), march).occurDate())
            .isEqualTo("2024-02-29T21:00:00");
        assertThat(parser.parse(createReportMessage("12月31日午後9時"), january).occurDate())
            .isEqualTo("2026-12-31T21:00:00");
        long utcPreviousDay = OffsetDateTime.parse("2026-10-24T16:00:00Z").toInstant().toEpochMilli();
        assertThat(parser.parse(createReportMessage("10月25日午前0時"), utcPreviousDay).occurDate())
            .isEqualTo("2026-10-25T00:00:00");
    }

    /**
     * 日時直後の読点がない文章でも住所境界を認識し、文字参照のエラー項目を取り違えないことを確認する。
     */
    @Test
    void parsesWithoutACommaBetweenDateAndAddress() {
        String message = createReportMessage("1月4日午後9時").replace("9時、", "9時");
        assertThat(parser.parse(message, EVENT_TIMESTAMP).district()).isEqualTo("高宮1丁目");
        reject(message.replace("高宮", "高&amp;宮"), ReportErrorEnum.INVALID_ADDRESS);
        reject(createReportMessage("1月4日午後9時&amp;"), ReportErrorEnum.INVALID_DATETIME);
    }

    /**
     * 不存在日・曖昧時刻・欠落・未来を拒否し、自動補正や未来時刻を理由にした年の再選択をしないことを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "", "　", "2026年2月29日午後9時", "2026年4月31日午後9時", "2月30日午後9時",
        "13月1日午前0時", "0月1日午前0時", "1月0日午前0時", "1月4日午後1時60分",
        "1月4日午後13時", "1月4日夕方ころ", "1月4日夜ころ", "1月4日",
        "2026年10月26日午後9時", "2026年10月25日午後9時", "10月25日午後9時",
        "令和0年1月4日午後9時", "2026年1月4日午後1時30分半", "2026年1月4日午後1時 30分"
    })
    void rejectsInvalidDateTimes(String dateTimeText) {
        reject(createReportMessage(dateTimeText), ReportErrorEnum.INVALID_DATETIME);
    }

    /**
     * 期間と複数日時を拒否し、先頭の日時だけを登録しないことを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "1月4日午後1時から1月5日午後2時まで",
        "1月4日午後1時ころから午後2時ころまで",
        "1月4日午後1時～午後2時",
        "1月4日午後1時及び1月5日午後2時",
        "1月4日午後1時、1月5日午後2時"
    })
    void rejectsUnsupportedDateTimeRanges(String dateTimeText) {
        reject(createReportMessage(dateTimeText), ReportErrorEnum.UNSUPPORTED_DATETIME);
    }

    /**
     * 基準時刻の欠落を拒否し、入力値が診断情報へ流出しないことを確認する。
     */
    @Test
    void rejectsMissingEventTime() {
        String message = createReportMessage("1月4日午後9時");
        ReportException error = assertThrows(ReportException.class, () -> parser.parse(message, null));
        assertThat(error.getErrorType()).isEqualTo(ReportErrorEnum.INVALID_DATETIME);
        assertThat(error.getItem()).isEqualTo("eventTimestamp");
        assertThat(error.getDetail()).isEqualTo("イベントタイムスタンプが指定されていません");
        assertThat(error.getCause()).isNull();
        assertThat(error.getDetail()).doesNotContain(message);
    }

    /**
     * 地名の漢数字は保持し、丁目と番地だけ数値変換することを確認する。
     */
    @ParameterizedTest
    @CsvSource({"五十川二丁目十一番,五十川2丁目,11番", "一番町四丁目2番,一番町4丁目,2番",
        "向野一丁目1-1,向野1丁目,1-1", "井尻四丁目2番3号,井尻4丁目,2-3号"})
    void convertsOnlyAddressNumbers(String address, String district, String details) {
        ParsedReportMessage parsedReport = parser.parse(
            createReportMessage("1月4日午後9時").replace("高宮1丁目11番", address), EVENT_TIMESTAMP);
        assertThat(parsedReport.district()).isEqualTo(district);
        assertThat(parsedReport.addressDetails()).isEqualTo(details);
    }

    /**
     * 施設・駅と車内の場所情報を概要へ残し、NFKCで長さが変わる場合も原文の表記を失わないことを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {"公園で、", "マンションで、", "駐車場で、", "高宮駅付近の路上で、",
        "七隈小学校グラウンドで、", "ﾊﾟｰｸ施設で、", "走行中のバス車内で、"})
    void preservesPlaceContextAndOriginalCharacters(String place) {
        String body = "女児が男から「何年生？」と声を掛けられました。\n　男は３０歳代、Ｔシャツでした。";
        String message = "1月4日午後9時、福岡市南区高宮1丁目の" + place + body;
        ParsedReportMessage parsedReport = parser.parse(message, EVENT_TIMESTAMP);
        assertThat(parsedReport.district()).isEqualTo("高宮1丁目");
        assertThat(parsedReport.summary())
            .isEqualTo(place + "女児が男から「何年生？」と声を掛けられました。 男は３０歳代、Ｔシャツでした。");
    }

    /**
     * 半角カナと濁点が合成される位置でも、正規化後の境界を原文上の正しい位置へ戻せることを確認する。
     */
    @Test
    void mapsNormalizedBoundaryAfterCombinedCharacters() {
        String originalText = "ﾊﾟーク";
        String normalizedText = ReportMessageTextUtils.normalizeForParsing(originalText);

        assertThat(ReportMessageTextUtils.toOriginalIndex(originalText, normalizedText.indexOf("ー")))
            .isEqualTo(2);
    }

    /**
     * 誤記、県外、町域欠落、人物特徴内の住所を正常住所として救済しないことを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {"福岡南区高宮1丁目11番", "東京都福岡市南区高宮1丁目11番",
        "福岡市南区", "福岡市南区西鉄井尻駅", "男は福岡市南区高宮1丁目11番",
        "福岡市南区高宮1丁目、福岡市中央区春吉2丁目", "福岡市南区高宮1丁目、春吉2丁目"})
    void rejectsInvalidAddresses(String address) {
        reject(
            "1月4日午後9時、" + address + "付近の路上で、女性が男につきまとわれました。",
            ReportErrorEnum.INVALID_ADDRESS
        );
    }

    /**
     * 概要または事案内容が空の入力を拒否することを確認する。
     */
    @Test
    void rejectsEmptySummary() {
        reject("1月4日午後9時、福岡市南区高宮1丁目の路上で、", ReportErrorEnum.INVALID_SUMMARY);
        reject("1月4日午後9時、福岡市南区高宮1丁目の公園で、", ReportErrorEnum.INVALID_SUMMARY);
    }

    /**
     * HTML文字参照をデコードせず、対象項目のエラーにすることを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {"&amp;", "&#12354;", "&#x3042;"})
    void rejectsHtmlReferencesByField(String entity) {
        reject(createReportMessage("1月" + entity + "4日午後9時"), ReportErrorEnum.INVALID_DATETIME);
        reject(
            createReportMessage("1月4日午後9時").replace("高宮", "高" + entity + "宮"),
            ReportErrorEnum.INVALID_ADDRESS
        );
        reject(createReportMessage("1月4日午後9時") + entity, ReportErrorEnum.INVALID_SUMMARY);
        reject(
            createReportMessage("1月4日午後9時").replace("付近の路上", "の" + entity + "公園"),
            ReportErrorEnum.INVALID_SUMMARY
        );
    }

    /**
     * 指定した日時を含むテスト用の不審者情報メッセージを作る。
     * @param dateTimeText 日時表記
     * @return テスト用の不審者情報メッセージ
     */
    private String createReportMessage(String dateTimeText) {
        return dateTimeText + "、福岡市南区高宮1丁目11番付近の路上で、女性が男につきまとわれました。";
    }

    /**
     * 指定種別で拒否されることを確認する。レスポンス: なし。
     * @param message 入力
     * @param expectedError 期待するエラー種別
     */
    private void reject(String message, ReportErrorEnum expectedError) {
        ReportException error = assertThrows(
            ReportException.class, () -> parser.parse(message, EVENT_TIMESTAMP));
        assertThat(error.getErrorType()).isEqualTo(expectedError);
    }
}
