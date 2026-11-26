package suspiciouspersonmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;

/**
 * 署名検証済みのLINE Webhookを非同期に処理し、不審者情報の登録と失敗時の返信を行う。
 */
@Slf4j
@Service
public class LineWebhookService {

    private final ReportService reportService;
    private final LineReplyService lineReplyService;
    private final TaskExecutor taskExecutor;

    /**
     * Spring Bootのタスク実行基盤で登録・返信を行うWebhookサービスを生成する。
     *
     * @param reportService 不審者情報の登録処理
     * @param lineReplyService 登録失敗時のLINE返信処理
     * @param taskExecutor アプリケーションの非同期タスク実行基盤
     */
    public LineWebhookService(ReportService reportService, LineReplyService lineReplyService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.reportService = reportService;
        this.lineReplyService = lineReplyService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Webhook内のイベントを一つのタスクとして引き渡し、登録・返信の完了を待たずに戻る。
     * レスポンス: なし。
     *
     * @param events 署名検証済みWebhookのイベント配列
     */
    public void submitEvents(JsonNode events) {
        taskExecutor.execute(() -> processEvents(events));
    }

    /**
     * 同じWebhookのイベントを順番に処理し、予期しない失敗時も後続イベントへ進む。
     * レスポンス: なし。
     *
     * @param events 登録・返信対象のイベント配列
     */
    private void processEvents(JsonNode events) {
        for (JsonNode event : events) {
            try {
                processEvent(event);
            } catch (Exception e) {
                // 例外メッセージや原因には非公開値が含まれ得るため、種別だけを記録する。
                log.error("LINE Webhookのイベント処理に失敗しました。stage=webhook_event_processing, exceptionType={}",
                    e.getClass().getSimpleName());
            }
        }
    }

    /**
     * 単一イベントから不審者情報を登録し、登録失敗時はLINEへエラーを返信する。
     * ユーザーIDまたはテキストがnull・空文字の場合は処理しない。レスポンス: なし。
     *
     * @param event 署名検証済みのLINE WebhookイベントJSON
     */
    public void processEvent(JsonNode event) {
        String userId = event.path("source").path("userId").asText(null);
        String text = event.path("message").path("text").asText(null);

        if (StringUtils.isNotEmpty(userId) && StringUtils.isNotEmpty(text)) {
            try {
                processReportEvent(userId, text, event);
            } catch (Exception e) {
                // ReportExceptionはこのイベントのエラーを記録済みのため、再記録しない。
                if (!(e instanceof ReportException)) {
                    log.error("LINE Webhookのイベント処理に失敗しました。stage=webhook_event_processing, exceptionType={}",
                        e.getClass().getSimpleName());
                }
                lineReplyService.replyRegistrationError(event.path("replyToken").asText(null));
            }
        }
    }

    /**
     * イベント時刻を検証し、登録トランザクションの完了まで不審者情報の処理を実行する。
     * レスポンス: なし。
     *
     * @param userId 登録元のLINEユーザーID
     * @param text 不審者情報メッセージ
     * @param event LINE WebhookのイベントJSON
     */
    private void processReportEvent(String userId, String text, JsonNode event) {
        Long eventTimestamp;
        try {
            eventTimestamp = extractEventTimestamp(event);
        } catch (ReportException e) {
            // ReportServiceへ到達しない入力エラーのため、ここで一度だけ記録する。
            log.error("不審者情報の解析に失敗しました。stage={}, error={}, item={}, reason={}",
                e.getStage().getLogValue(), e.getErrorType(), e.getItem(), e.getDetail());
            throw e;
        }
        reportService.registerReport(userId, text, eventTimestamp);
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
