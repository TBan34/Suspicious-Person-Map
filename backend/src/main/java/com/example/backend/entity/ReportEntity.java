package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 不審者情報Entity
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class ReportEntity {
    // ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ユーザーID(LINE)
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    // タグ
    @Column(name = "tag", length = 100)
    private String tag;

    // 都道府県
    @Column(name = "prefecture", length = 4)
    private String prefecture;

    // 市区町村
    @Column(name = "municipality", length = 7)
    private String municipality;

    // 丁目
    @Column(name = "district", length = 100)
    private String district;

    // 番地以降
    @Column(name = "address_details", length = 255)
    private String addressDetails;

    // 緯度
    @Column(name = "latitude", nullable = false)
    private double latitude;

    // 経度
    @Column(name = "longitude", nullable = false)
    private double longitude;

    // 概要
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // 作成日
    @CreatedDate
    @Column(name = "created", nullable = false, updatable = false)
    private LocalDateTime created;
}
