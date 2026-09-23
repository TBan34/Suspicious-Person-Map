package com.example.backend.exception;

import com.example.backend.common.constant.ReportProcessingStageEnum;

/**
 * 不審者情報の処理中に発生した例外と、ログ出力に必要な処理情報を保持する例外。
 */
public class ReportProcessingException extends RuntimeException {

    private final ReportProcessingStageEnum stage;
    private final String item;
    private final String value;

    /**
     * 原因例外とログ出力用の処理情報を保持する不審者情報処理例外を生成する。
     * レスポンス: 処理段階・対象項目・値・原因例外を保持する ReportProcessingException インスタンス。
     *
     * @param stage 例外が発生した処理段階
     * @param item 例外の調査対象となる項目名
     * @param value ログ出力を許可する対象項目の値または状態
     * @param cause 不審者情報の処理中に発生した原因例外
     */
    public ReportProcessingException(
            ReportProcessingStageEnum stage,
            String item,
            String value,
            Throwable cause) {
        super("不審者情報の処理に失敗しました。", cause);
        this.stage = stage;
        this.item = item;
        this.value = value;
    }

    /**
     * 例外が発生した処理段階を取得する。
     *
     * @return 例外が発生した処理段階
     */
    public ReportProcessingStageEnum getStage() {
        return stage;
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
     * ログ出力を許可する対象項目の値または状態を取得する。
     *
     * @return ログ出力を許可する対象項目の値または状態
     */
    public String getValue() {
        return value;
    }
}
