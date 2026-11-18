package suspiciouspersonmap.entity;

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

    // タグ1
    @Column(name = "tag1", length = 10)
    private String tag1;

    // タグ2
    @Column(name = "tag2", length = 10)
    private String tag2;

    // タグ3
    @Column(name = "tag3", length = 10)
    private String tag3;

    // 発生日時
    @Column(name = "occur_datetime", length = 100)
    private LocalDateTime occurDatetime;

    // 都道府県
    @Column(name = "prefecture", length = 4)
    private String prefecture;

    // 市区町村
    @Column(name = "municipality", length = 7)
    private String municipality;

    // 町域・丁目
    @Column(name = "town_area", length = 100)
    private String townArea;

    // 番地・号
    @Column(name = "block_and_house_number", length = 255)
    private String blockAndHouseNumber;

    // 緯度
    @Column(name = "latitude", nullable = false)
    private double latitude;

    // 経度
    @Column(name = "longitude", nullable = false)
    private double longitude;

    // 概要
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // 作成日時
    @CreatedDate
    @Column(name = "create_datetime", nullable = false, updatable = false)
    private LocalDateTime createDatetime;
}
