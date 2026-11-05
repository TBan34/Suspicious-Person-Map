package suspiciouspersonmap.service.parsing;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Objects;
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
    private static final Pattern UNSUPPORTED_CONTINUATION_PATTERN = Pattern.compile(
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
     * @param eventTimestamp LINEイベントのUnix時間（ミリ秒）。欠落時はnull
     * @return イベント発生時刻を日本標準時へ変換した日時
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
            LocalDateTime japanDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(eventTimestamp), JAPAN_TIME_ZONE);
            // LINEのイベント時刻として負数は許可せず、DBで扱えない5桁年以上の年も拒否する。
            if (eventTimestamp < 0 || japanDateTime.getYear() > 9999) {
                throw new DateTimeException("out of range");
            }
            return japanDateTime;
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
        Objects.requireNonNull(normalizedMessage, "normalizedMessage");
        Objects.requireNonNull(japanDateTime, "japanDateTime");

        Matcher dateTimeMatcher = DATE_TIME_PATTERN.matcher(normalizedMessage);
        if (!dateTimeMatcher.lookingAt()) {
            throw invalidDateTime();
        }

        String messageAfterDateTime = normalizedMessage.substring(dateTimeMatcher.end());
        validateDateTimeContinuation(messageAfterDateTime);

        try {
            LocalDate occurDate = parseOccurDate(dateTimeMatcher, japanDateTime.toLocalDate());
            LocalTime occurTime = parseOccurTime(dateTimeMatcher);
            LocalDateTime occurDateTime = occurDate.atTime(occurTime);
            if (occurDateTime.isAfter(japanDateTime)) {
                throw new ReportException(ReportErrorEnum.INVALID_DATETIME, "occurDate", "発生日時が未来です");
            }
            return new ParsedDateTime(occurDateTime, dateTimeMatcher.end());
        } catch (DateTimeException | NumberFormatException e) {
            throw invalidDateTime();
        }
    }

    /**
     * 日時直後にHTML文字参照、未対応の期間表現、時刻の解析残りがないことを検証する。
     * レスポンス: なし。
     * @param messageAfterDateTime 正規化済みメッセージの日時より後ろの文字列
     */
    private void validateDateTimeContinuation(String messageAfterDateTime) {
        String messageWithoutLeadingWhitespace = messageAfterDateTime.stripLeading();
        if (messageWithoutLeadingWhitespace.startsWith("&")) {
            ReportMessageTextUtils.validateNoHtmlCharacterReference(
                messageAfterDateTime, ReportErrorEnum.INVALID_DATETIME, "occurDate");
        }
        if (UNSUPPORTED_CONTINUATION_PATTERN.matcher(messageAfterDateTime).find()) {
            throw new ReportException(ReportErrorEnum.UNSUPPORTED_DATETIME, "occurDate", "未対応の日時形式です");
        }
        if (!StringUtils.isEmpty(messageWithoutLeadingWhitespace)
                && (Character.isDigit(messageWithoutLeadingWhitespace.charAt(0))
                    || "半分時".indexOf(messageWithoutLeadingWhitespace.charAt(0)) >= 0)) {
            // 「30分半」「 30分」などの解析できなかった時刻部分を住所として扱わない。
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
        int month = Integer.parseInt(dateTimeMatcher.group("month"));
        int day = Integer.parseInt(dateTimeMatcher.group("day"));
        String yearText = dateTimeMatcher.group("year");
        if (yearText == null) {
            return inferMostRecentDate(month, day, eventDate);
        }

        int year = yearText.equals("元") ? 1 : Integer.parseInt(yearText);
        String reiwaText = dateTimeMatcher.group("reiwa");
        if (year < 1 || (yearText.equals("元") && reiwaText == null)) {
            throw invalidDateTime();
        }
        if (reiwaText != null) {
            year += 2018;
        }

        LocalDate occurDate = LocalDate.of(year, month, day);
        if (reiwaText != null && occurDate.isBefore(LocalDate.of(2019, 5, 1))) {
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
    private LocalDate inferMostRecentDate(int month, int day, LocalDate eventDate) {
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
