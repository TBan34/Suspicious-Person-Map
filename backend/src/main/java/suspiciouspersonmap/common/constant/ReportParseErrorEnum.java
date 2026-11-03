package suspiciouspersonmap.common.constant;

/** 入力の値を含めずに識別できる解析エラー種別。 */
public enum ReportParseErrorEnum {
    /** 日時が欠落、不正、または未来日時。 */
    INVALID_DATETIME,
    /** 日時の期間や複数日時など、現在は扱わない形式。 */
    UNSUPPORTED_DATETIME,
    /** 住所が欠落、不正、または概要との境界を確定できない。 */
    INVALID_ADDRESS,
    /** 概要が欠落または不正。 */
    INVALID_SUMMARY,
    /** 合意済みの正規タグを判定できない。 */
    UNCLASSIFIABLE_TAG,
    /** 重複整理後のタグが保存上限を超えている。 */
    TOO_MANY_TAGS
}
