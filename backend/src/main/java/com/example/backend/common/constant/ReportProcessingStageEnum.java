package com.example.backend.common.constant;

/**
 * 不審者情報の登録処理で例外が発生した処理段階を表す列挙型。
 */
public enum ReportProcessingStageEnum {
    INPUT_VALIDATION("input_validation"),
    REPORT_MESSAGE_TRANSFORMATION("report_message_transformation"),
    GEOCODING("geocoding"),
    REPORT_CREATION("report_creation"),
    DATABASE_SAVE("database_save");

    private final String logValue;

    /**
     * 処理段階とログ出力用の値を対応付ける。
     * レスポンス: 指定されたログ値を持つ ReportProcessingStageEnum インスタンス。
     *
     * @param logValue ログへ出力する処理段階の値
     */
    ReportProcessingStageEnum(String logValue) {
        this.logValue = logValue;
    }

    /**
     * 処理段階のログ出力用の値を取得する。
     *
     * @return ログへ出力する処理段階の値
     */
    public String getLogValue() {
        return logValue;
    }
}
