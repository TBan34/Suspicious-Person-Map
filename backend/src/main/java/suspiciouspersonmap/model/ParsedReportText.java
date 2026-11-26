package suspiciouspersonmap.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 不審者情報テキストの解析と入力検証を完了し、Geocoding・保存へ渡せる項目。
 * @param tags 1～3件の正規タグ
 * @param occurDatetime 発生日時
 * @param prefecture 都道府県
 * @param municipality 市区町村
 * @param townArea 町域・丁目
 * @param blockAndHouseNumber 番地・号。記載がなければnull
 * @param description 日時と住所の一般的な導入部を除いた概要
 */
public record ParsedReportText(List<String> tags, LocalDateTime occurDatetime, String prefecture,
        String municipality, String townArea, String blockAndHouseNumber, String description) {}
