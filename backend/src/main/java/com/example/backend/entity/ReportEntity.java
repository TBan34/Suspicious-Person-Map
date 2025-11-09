package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class ReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "tag", length = 100)
    private String tag;

    @Column(name = "prefecture", length = 50)
    private String prefecture;

    @Column(name = "municipality", length = 100)
    private String municipality;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "address_details", length = 255)
    private String addressDetails;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @CreatedDate
    @Column(name = "created", nullable = false, updatable = false)
    private LocalDateTime created;
}
