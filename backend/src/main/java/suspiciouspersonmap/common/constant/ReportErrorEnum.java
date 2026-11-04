package suspiciouspersonmap.common.constant;

/**
 * 不審者情報の登録処理で発生するエラー種別。
 */
public enum ReportErrorEnum {
    /** 必須入力が欠落している。 */
    INVALID_INPUT(ReportProcessingStageEnum.INPUT_VALIDATION),
    /** 日時が欠落、不正、または未来日時。 */
    INVALID_DATETIME(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION),
    /** 日時の期間や複数日時など、現在は扱わない形式。 */
    UNSUPPORTED_DATETIME(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION),
    /** 住所が欠落、不正、または概要との境界を確定できない。 */
    INVALID_ADDRESS(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION),
    /** 概要が欠落または不正。 */
    INVALID_SUMMARY(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION),
    /** 合意済みの正規タグを判定できない。 */
    UNCLASSIFIABLE_TAG(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION),
    /** 重複整理後のタグが保存上限を超えている。 */
    TOO_MANY_TAGS(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION),
    /** Geocoding処理に失敗した。 */
    GEOCODING_FAILED(ReportProcessingStageEnum.GEOCODING),
    /** データベース登録用Entityの生成に失敗した。 */
    REPORT_CREATION_FAILED(ReportProcessingStageEnum.REPORT_CREATION),
    /** データベースへの保存に失敗した。 */
    DATABASE_SAVE_FAILED(ReportProcessingStageEnum.DATABASE_SAVE);

    private final ReportProcessingStageEnum stage;

    /**
     * エラー種別と、エラーが発生する処理段階を対応付ける。
     *
     * @param stage エラーが発生する処理段階
     */
    ReportErrorEnum(ReportProcessingStageEnum stage) {
        this.stage = stage;
    }

    /**
     * エラーが発生する処理段階を取得する。
     *
     * @return エラーが発生する処理段階
     */
    public ReportProcessingStageEnum getStage() {
        return stage;
    }
}
