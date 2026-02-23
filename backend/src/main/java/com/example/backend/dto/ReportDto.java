package com.example.backend.dto;

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

    // ユーザーID(LINE)
    private String userId;

    // タグ1
    private String tag1;

    // タグ2
    private String tag2;

    // タグ3
    private String tag3;

    // 発生日時
    private LocalDateTime occurDate;

    // 都道府県
    private String prefecture;

    // 市区町村
    private String municipality;

    // 丁目
    private String district;

    // 番地以降
    private String addressDetails;

    // 緯度
    private double latitude;

    // 経度
    private double longitude;

    // 概要
    private String summary;

    // 作成日
    private LocalDateTime created;
}

