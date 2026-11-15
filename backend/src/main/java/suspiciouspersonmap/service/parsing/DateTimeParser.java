package suspiciouspersonmap.service.parsing;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;

/** 不審者情報メッセージの先頭にある単一の発生日時を厳密に解析する。 */
@Component
public class DateTimeParser {
    private static final ZoneId JAPAN_TIME_ZONE = ZoneId.of("Asia/Tokyo");

    // 年省略・西暦・令和と「午前／午後＋数値時刻」を解析する。
    // 例:「1月4日午後1時半」「2026年1月4日午後1時30分頃」「令和8年1月4日午前0時」。
    // 日付だけ、夕方などの曖昧時刻、24時間表記は対象外とする。
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
        "^(?:(?<reiwa>令和)?(?<year>[0-9]{1,4}|元)年)?"
        + "(?<month>[0-9]{1,2})月(?<day>[0-9]{1,2})日"
        + "(?<ampm>午前|午後)(?<hour>[0-9]{1,2})時"
        + "(?:(?<half>半)|(?<minute>[0-9]{1,2})分)?(?:ころ|頃)?"
    );
    // 先頭日時の直後に「から」「～」や別日が続く期間・複数日時を検出する。
    // 本文中の「男から」などを誤検出しないよう、日時直後だけを検査する。
    private static final Pattern UNSUPPORTED_DATE_TIME_FORMAT_PATTERN = Pattern.compile(
        "^\\s*(?:から|まで|[～〜~－-]|及び|および|と?、?\\s*(?:(?:令和)?[0-9]+年)?[0-9]+月[0-9]+日)"
    );

    /**
     * 解析した発生日時と、正規化済みメッセージ上で日時が終了する位置。
     * @param occurDate 発生日時
     * @param dateTimeEndIndex 正規化済みメッセージ上の日時終了位置
     */
    public record ParsedDateTime(LocalDateTime occurDate, int dateTimeEndIndex) {}

    /**
     * LINEイベントのUnix時間を日本標準時へ変換する。
     * @param eventTimestamp LINEイベントのUnix時間（ミリ秒）
     * @return イベント発生時刻を日本標準時へ変換した日時
     * @throws ReportException イベント時刻が欠落、または日本標準時へ変換できない場合
     */
    public LocalDateTime toJapanLocalDateTime(Long eventTimestamp) {
        if (eventTimestamp == null) {
            throw new ReportException(
                ReportErrorEnum.INVALID_DATETIME,
                "eventTimestamp",
                "イベントタイムスタンプが指定されていません"
            );
        }
        try {
            return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(eventTimestamp), JAPAN_TIME_ZONE);
        } catch (DateTimeException e) {
            throw new ReportException(
                ReportErrorEnum.INVALID_DATETIME,
                "eventTimestamp",
                "イベントタイムスタンプが不正です"
            );
        }
    }

    /**
     * 正規化済みメッセージの先頭から発生日時を抽出し、未来日時でないことを検証する。
     * @param normalizedMessage 正規化済みの不審者情報メッセージ
     * @param japanDateTime LINEイベント時刻を日本標準時へ変換した日時
     * @return 発生日時と、正規化済みメッセージ上の日時終了位置
     */
    public ParsedDateTime parse(String normalizedMessage, LocalDateTime japanDateTime) {
        Matcher dateTimeMatcher = DATE_TIME_PATTERN.matcher(normalizedMessage);
        if (!dateTimeMatcher.lookingAt()) {
            throw invalidDateTime();
        }

        // 日時の直後に、未対応の期間・複数日時表現が続いていないか確認する。
        String messageAfterDateTime = normalizedMessage.substring(dateTimeMatcher.end());
        if (UNSUPPORTED_DATE_TIME_FORMAT_PATTERN.matcher(messageAfterDateTime).find()) {
            throw new ReportException(ReportErrorEnum.UNSUPPORTED_DATETIME, "occurDate", "未対応の日時形式です");
        }

        try {
            LocalDateTime occurDateTime = parseOccurDate(
                dateTimeMatcher,
                japanDateTime.toLocalDate()
            ).atTime(parseOccurTime(dateTimeMatcher));

            // LINEイベント時刻より未来の発生日時は受け付けない。
            if (occurDateTime.isAfter(japanDateTime)) {
                throw new ReportException(ReportErrorEnum.INVALID_DATETIME, "occurDate", "発生日時が未来です");
            }
            return new ParsedDateTime(occurDateTime, dateTimeMatcher.end());
        } catch (DateTimeException | NumberFormatException e) {
            throw invalidDateTime();
        }
    }

    /**
     * 日付部分を解析し、年省略時はイベント日以前で最も近い実在日を選ぶ。
     * @param dateTimeMatcher 日時表現に一致したMatcher
     * @param eventDate LINEイベントの日本日付
     * @return 検証済みの発生日
     */
    private LocalDate parseOccurDate(Matcher dateTimeMatcher, LocalDate eventDate) {
        // 日時表現から月日と年表記を取得する。
        int month = Integer.parseInt(dateTimeMatcher.group("month"));
        int day = Integer.parseInt(dateTimeMatcher.group("day"));
        String yearText = dateTimeMatcher.group("year");

        // 年省略時はイベント日以前で最も近い実在日を補完する。
        if (StringUtils.isEmpty(yearText)) {
            return resolveDateWithoutYear(month, day, eventDate);
        }

        // 年表記を数値化し、入力に令和が含まれるか取得する。
        int year = yearText.equals("元") ? 1 : Integer.parseInt(yearText);
        String eraName = dateTimeMatcher.group("reiwa");

        // 元号がない場合は西暦年として検証する。
        if (StringUtils.isEmpty(eraName)) {
            return LocalDate.of(year, month, day);
        }

        // 元号がある場合は令和年として検証する。
        LocalDate occurDate = LocalDate.of(year + 2018, month, day);
        if (occurDate.isBefore(LocalDate.of(2019, 5, 1))) {
            throw invalidDateTime();
        }
        return occurDate;
    }

    /**
     * 年が省略された月日について、イベント日以前で最も近い実在日を求める。
     * @param month 発生月
     * @param day 発生日
     * @param eventDate LINEイベントの日本日付
     * @return 補完した発生日
     */
    private LocalDate resolveDateWithoutYear(int month, int day, LocalDate eventDate) {
        MonthDay monthDay = MonthDay.of(month, day); // 2月30日などは年を探索せず拒否する。
        for (int year = eventDate.getYear(); year >= 1; year--) {
            if (monthDay.isValidYear(year)) {
                LocalDate candidateDate = monthDay.atYear(year);
                if (!candidateDate.isAfter(eventDate)) {
                    return candidateDate;
                }
            }
        }
        throw invalidDateTime();
    }

    /**
     * 午前・午後を含む時刻部分を24時間表記へ変換する。
     * @param dateTimeMatcher 日時表現に一致したMatcher
     * @return 検証済みの発生時刻
     */
    private LocalTime parseOccurTime(Matcher dateTimeMatcher) {
        int hour = Integer.parseInt(dateTimeMatcher.group("hour"));
        int minute = dateTimeMatcher.group("half") != null ? 30
            : dateTimeMatcher.group("minute") == null ? 0
            : Integer.parseInt(dateTimeMatcher.group("minute"));
        if (hour > 11) {
            throw invalidDateTime();
        }
        int hourOfDay = hour + (dateTimeMatcher.group("ampm").equals("午後") ? 12 : 0);
        return LocalTime.of(hourOfDay, minute);
    }

    /**
     * 入力値を含まない日時不正例外を生成する。
     * @return 日時不正例外
     */
    private ReportException invalidDateTime() {
        return new ReportException(ReportErrorEnum.INVALID_DATETIME, "occurDate", "発生日時が不正です");
    }
}
