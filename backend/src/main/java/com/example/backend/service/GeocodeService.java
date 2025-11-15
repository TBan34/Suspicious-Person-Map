package com.example.backend.service;

import com.example.backend.model.GeoPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google MapsのGeocoding APIを使用し、座標情報を取得する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodeService {

    // Geocoding　API
    private static class GEOCODING_API {

        //　APIの返却ステータス
        private static class RESPONSE_STATUS {
            private static final String OK = "OK";
            private static final String ZERO_RESULTS = "ZERO_RESULTS";
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.api.key}")
    private String apiKey;

    /*
     * 座標情報を取得する。
     * address: 住所情報
     * return: 座標情報
     */
    public GeoPoint getLatLng(String address) {

        try {
            // Geocoding API URL
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                    URLEncoder.encode(address, StandardCharsets.UTF_8) +
                    "&key=" + apiKey;
            log.debug("Calling Geocoding API for address: {}", address);

            // Geocoding API実行
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // APIのステータスチェック
            String status = jsonNode.get("status").asText();
            // 異常終了または返却結果なしの場合
            if (!StringUtils.equals(GEOCODING_API.RESPONSE_STATUS.OK, status)) {

                // 返却結果なしの場合
                if (StringUtils.equals(GEOCODING_API.RESPONSE_STATUS.ZERO_RESULTS, status)) {
                    log.warn("The address was not found");
                    throw new RuntimeException("The address was not found" + address);

                // 異常終了の場合
                } else {
                    String errorMessage = jsonNode.has("error_message") 
                        ? jsonNode.get("error_message").asText() 
                        : "Unknown error";
                    log.error("Geocoding API error - Status: {}, Message: {}", status, errorMessage);
                    throw new RuntimeException("Geocoding API error: " + status + " - " + errorMessage);
                }
            }

            JsonNode results = jsonNode.get("results");
            // 返却結果がnullまたは配列以外または空の場合、エラー
            if (results == null || !results.isArray() || results.isEmpty()) {
                log.warn("No results found for address: {}", address);
                throw new RuntimeException("No results found for address: " + address);
            }

            // Geometryがnullの場合、エラー
            JsonNode geometry = results.get(0).get("geometry");
            if (geometry == null) {
                throw new RuntimeException("Invalid response structure: missing geometry");
            }
            // 緯度経度が取得できない場合、エラー
            JsonNode location = geometry.get("location");
            if (location == null || !location.has("lat") || !location.has("lng")) {
                throw new RuntimeException("Invalid response structure: missing location coordinates");
            }
            double lat = location.get("lat").asDouble();
            double lng = location.get("lng").asDouble();
            log.debug("Successfully geocoded address: {} -> lat: {}, lng: {}", address, lat, lng);

            return new GeoPoint(lat, lng);

        // RestTemplateの通信エラー
        } catch (RestClientException e) {
            log.error("Failed to call Geocoding API for address: {}", address, e);
            throw new RuntimeException("Failed to call Geocoding API for address: " + address, e);
        
        // 上記以外のエラー
        } catch (Exception e) {
            log.error("Geocoding failed for address: {}", address, e);
            throw new RuntimeException("Geocoding failed for address: " + address, e);
        }
    }
}
