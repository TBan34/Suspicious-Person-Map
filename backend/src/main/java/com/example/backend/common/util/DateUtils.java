package com.example.backend.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.text.Normalizer;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

public class DateUtils {

  // 日時情報「YYYY年MM月午前/午後h時m分」のフォーマッター。時分は任意。
  private static final DateTimeFormatter FLEX_JPN_FORMATTER =
        new DateTimeFormatterBuilder()
            .appendPattern("yyyy年M月d日")
            .optionalStart()
                .appendPattern("a") // 午前/午後
                .appendPattern("K時") // 0-11 を許す（午後0時対応）
                .optionalStart()
                    .appendValue(ChronoField.MINUTE_OF_HOUR) // 1桁/2桁両対応
                    .appendLiteral("分")
                .optionalEnd()
            .optionalEnd()
            .parseDefaulting(ChronoField.AMPM_OF_DAY, 0)
            .parseDefaulting(ChronoField.HOUR_OF_AMPM, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.JAPAN);

      /*
     * String→LocalDateTimeの型変換
     * strDate: 日付情報（例：2025年9月8日午後6時10分）
     * return: 日付情報(LocalDateTime)
     */
      public static LocalDateTime parseToLocalDateTime(String strDate) {

        // Nullの場合、処理を終了
        if (StringUtils.isEmpty(strDate)) {
          return null;

        } else {
          LocalDateTime ldtDate;

          // 全角数字→半角数字
          strDate = Normalizer.normalize(strDate, Normalizer.Form.NFKC);
  
          // LocalDateTime変換処理
          try {
            TemporalAccessor ta = FLEX_JPN_FORMATTER.parse(strDate);
            LocalDate date = LocalDate.from(ta);
            LocalTime time = LocalTime.from(ta);
            ldtDate = LocalDateTime.of(date, time);
  
          } catch (DateTimeParseException dtpEx) {
            throw new DateTimeParseException("DateTimeFormatterに不正な引数が渡されました。", strDate, 0);
  
          } catch (Exception ex) {
            throw ex;
          }
          
          return ldtDate;
        }
    }
}
