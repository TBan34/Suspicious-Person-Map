@Entity
@Table(name = "suspicious_activity_info")
public class SuspiciousActivityInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 住所情報
     */
    // 都道府県
    private String prefecture;
    // 市区町村
    private String municipality;
    // 丁目
    private String district;
    // 番地以降
    private String addressDetails;

    // 分類
    private String tag;
    // 事件概要
    private String summary;

    /**
     * 緯度経度
     * 緯度経度が不要な場合、削除する。
     */
    private Double latitude;
    private Double longitude;

    @CreationTimestamp
    private LocalDateTime created;

    // getters/setters
}
