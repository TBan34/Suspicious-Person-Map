package suspiciouspersonmap.exception;

import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.common.constant.ReportProcessingStageEnum;

/**
 * 不審者情報の処理中に発生した例外と、ログ出力に必要な処理情報を保持する例外。
 */
public class ReportException extends RuntimeException {

    private final ReportErrorEnum errorType;
    private final String item;
    private final String detail;

    /**
     * 原因例外を保持しない不審者情報処理例外を生成する。
     *
     * @param errorType エラー種別
     * @param item 例外の調査対象となる項目名
     * @param detail ログ出力を許可する対象項目の値、状態または原因
     */
    public ReportException(ReportErrorEnum errorType, String item, String detail) {
        this(errorType, item, detail, null);
    }

    /**
     * 原因例外とログ出力用の処理情報を持つ不審者情報処理例外を生成する。
     *
     * @param errorType エラー種別
     * @param item 例外の調査対象となる項目名
     * @param detail ログ出力を許可する対象項目の値、状態または原因
     * @param cause 不審者情報の処理中に発生した原因例外。保持しない場合はnull
     */
    public ReportException(
            ReportErrorEnum errorType,
            String item,
            String detail,
            Throwable cause) {
        super("不審者情報の処理に失敗しました。", cause);
        this.errorType = errorType;
        this.item = item;
        this.detail = detail;
    }

    /**
     * 例外が発生した処理段階を取得する。
     *
     * @return 例外が発生した処理段階
     */
    public ReportProcessingStageEnum getStage() {
        return errorType.getStage();
    }

    /**
     * エラー種別を取得する。
     *
     * @return エラー種別
     */
    public ReportErrorEnum getErrorType() {
        return errorType;
    }

    /**
     * 例外の調査対象となる項目名を取得する。
     *
     * @return 例外の調査対象となる項目名
     */
    public String getItem() {
        return item;
    }

    /**
     * ログ出力を許可する対象項目の値、状態または原因を取得する。
     *
     * @return 対象項目の値、状態または原因
     */
    public String getDetail() {
        return detail;
    }
}
