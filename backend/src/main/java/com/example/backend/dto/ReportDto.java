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
    private Long id;
    private String userId;
    private String tag;
    private String prefecture;
    private String municipality;
    private String district;
    private String addressDetails;
    private double latitude;
    private double longitude;
    private String summary;
    private LocalDateTime created;
}

