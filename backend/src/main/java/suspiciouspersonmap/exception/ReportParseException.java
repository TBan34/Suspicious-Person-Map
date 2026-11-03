package suspiciouspersonmap.exception;

import suspiciouspersonmap.common.constant.ReportParseErrorEnum;
import suspiciouspersonmap.common.constant.ReportProcessingStageEnum;

/** 入力値をログへ露出せず、解析エラーの種別・項目・原因を保持する例外。 */
public class ReportParseException extends ReportProcessingException {
    private final ReportParseErrorEnum errorType;
    private final String reason;

    /**
     * 安全な診断情報を保持する解析例外を生成する。
     * @param errorType エラー種別
     * @param item 不正な項目
     * @param reason アプリケーションが定義する原因（入力値を含めない）
     */
    public ReportParseException(ReportParseErrorEnum errorType, String item, String reason) {
        // reasonはアプリケーションが定義した安全な文言であり、入力値を含む原因例外は保持しない。
        super(ReportProcessingStageEnum.REPORT_MESSAGE_TRANSFORMATION, item);
        this.errorType = errorType;
        this.reason = reason;
    }

    /**
     * エラー種別を取得する。
     * @return エラー種別
     */
    public ReportParseErrorEnum getErrorType() {
        return errorType;
    }

    /**
     * 安全な原因を取得する。
     * @return 入力値を含まない原因
     */
    public String getReason() {
        return reason;
    }
}
