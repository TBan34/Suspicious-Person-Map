package suspiciouspersonmap.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 不審者情報メッセージの解析と入力検証を完了し、Geocoding・保存へ渡せる項目。
 * @param tags 1～3件の正規タグ
 * @param occurDate 発生日時
 * @param prefecture 都道府県
 * @param municipality 市区町村
 * @param district 町域・丁目
 * @param addressDetails 番地以降。記載がなければnull
 * @param summary 日時と住所の一般的な導入部を除いた概要
 */
public record ParsedReportMessage(List<String> tags, LocalDateTime occurDate, String prefecture,
        String municipality, String district, String addressDetails, String summary) {}
