package suspiciouspersonmap.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 不審者情報Dto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDto {
    // ID
    private Long id;

    // タグ1
    private String tag1;

    // タグ2
    private String tag2;

    // タグ3
    private String tag3;

    // 発生日時
    private LocalDateTime occurDatetime;

    // 都道府県
    private String prefecture;

    // 市区町村
    private String municipality;

    // 町域・丁目
    private String townArea;

    // 番地・号
    private String blockAndHouseNumber;

    // 緯度
    private double latitude;

    // 経度
    private double longitude;

    // 概要
    private String description;

    // 作成日時
    private LocalDateTime createDatetime;
}
