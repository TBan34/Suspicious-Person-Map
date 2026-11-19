package suspiciouspersonmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;

/**
 * 署名検証済みのLINE Webhookイベントから、不審者情報の登録処理を実行する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineWebhookService {

    private final ReportService reportService;

    /**
     * 単一イベントから登録に必要な項目を取得し、不審者情報を登録する。
     * ユーザーIDまたはテキストがnull・空文字の場合は処理しない。レスポンス: なし。
     *
     * @param event 署名検証済みのLINE WebhookイベントJSON
     */
    public void processEvent(JsonNode event) {
        String userId = event.path("source").path("userId").asText(null);
        String text = event.path("message").path("text").asText(null);

        if (StringUtils.isNotEmpty(userId) && StringUtils.isNotEmpty(text)) {
            Long eventTimestamp;
            try {
                eventTimestamp = extractEventTimestamp(event);
            } catch (ReportException e) {
                // ReportServiceへ到達しない入力エラーのため、ここで一度だけ記録する。
                log.error("不審者情報の解析に失敗しました。stage={}, error={}, item={}, reason={}",
                    e.getStage().getLogValue(), e.getErrorType(), e.getItem(), e.getDetail());
                throw e;
            }
            reportService.processReportMessage(userId, text, eventTimestamp);
        }
    }

    /**
     * LINEイベントからミリ秒単位のUnix時間を検証して取得する。
     *
     * @param event LINE WebhookのイベントJSON
     * @return LINEイベント時刻
     * @throws ReportException イベント時刻が欠落、またはlongの範囲内の整数として取得できない場合
     */
    private Long extractEventTimestamp(JsonNode event) {
        JsonNode timestampNode = event.path("timestamp");
        if (timestampNode.isMissingNode() || timestampNode.isNull()) {
            throw new ReportException(
                ReportErrorEnum.INVALID_DATETIME,
                "eventTimestamp",
                "イベントタイムスタンプが指定されていません"
            );
        }
        if (!timestampNode.isIntegralNumber() || !timestampNode.canConvertToLong()) {
            throw new ReportException(
                ReportErrorEnum.INVALID_DATETIME,
                "eventTimestamp",
                "イベントタイムスタンプの型または範囲が不正です"
            );
        }
        return timestampNode.longValue();
    }
}
