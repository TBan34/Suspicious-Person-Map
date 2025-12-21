package com.example.backend.service;

import com.example.backend.model.GeoPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
            // private static final String ZERO_RESULTS = "ZERO_RESULTS";
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
            List<String> fallbackAddresses = createFallbackAddresses(address);

            for (String fallbackAddress : fallbackAddresses) {
                GeoPoint point = callGeocodingApi(fallbackAddress);
                if (point != null) {
                    log.info("Geocoding success with fallback address: {}", fallbackAddress);
                    return point;
                }
            }

            throw new RuntimeException("All geocoding attempts failed for address: " + address);

        } catch (Exception e) {
            log.error("Geocoding failed for address: {}", address, e);
            throw new RuntimeException("Geocoding failed for address: " + address, e);
        }
    }

    /*
     * 住所情報（フォールバック用）を取得する。
     * address: 住所情報
     * return: 住所情報（フォールバック用）
     */
    private List<String> createFallbackAddresses(String address) {
    
        // NOTE: より正確かつ詳細に住所情報をリスト化するようロジック改善したい。
        // 末尾ハイフンの場合、削除
        if (StringUtils.equals("-", address.substring(address.length() -1))) {
            address = StringUtils.removeEnd(address, "-");
        }

        List<String> fallbackAddresses = new ArrayList<>();

        fallbackAddresses.add(address);

        // NOTE: 建物名や部屋番号を除外するロジック検討が必要
        // 番地まで
        fallbackAddresses.add(address.replaceAll("-\\d+$", ""));

        // 丁目まで
        fallbackAddresses.add(address.replaceAll("-\\d+$", ""));

        // 町名まで
        fallbackAddresses.add(address.replaceAll("\\d+$", ""));

        // 区まで
        fallbackAddresses.add(address.replaceAll("区.*$", "区"));

        // 市町村まで
        fallbackAddresses.add(address.replaceAll("^(.*?[市町村]).*$", "$1"));

        return fallbackAddresses.stream()
                .distinct()
                .toList();
    }

        /*
     * 座標情報を取得する。
     * address: 住所情報
     * return: 座標情報
     */
    private GeoPoint callGeocodingApi(String address) {
        try {
            URI uri = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/geocode/json")
                .queryParam("address", address)
                .queryParam("language", "ja")
                .queryParam("region", "jp")
                .queryParam("key", apiKey)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
            log.debug("Calling Geocoding API for address: {}", address);
            log.info("Geocoding request URI = {}", uri);

            // Geocoding API実行
            String response = restTemplate.getForObject(uri, String.class);
            log.info("Geocoding raw response for {}: {}", address, response);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // APIのステータスチェック
            String status = jsonNode.get("status").asText();
            // 異常終了または返却結果なしの場合
            if (!StringUtils.equals(GEOCODING_API.RESPONSE_STATUS.OK, status)) {

                String errorMessage = jsonNode.has("error_message")
                    ? jsonNode.get("error_message").asText()
                    : "no error_message";

                log.warn("Geocoding API status not OK. status={}, error_message={}, address={}",
                    status, errorMessage, address);

                return null;
                // 返却結果なしの場合
                // if (StringUtils.equals(GEOCODING_API.RESPONSE_STATUS.ZERO_RESULTS, status)) {
                //     log.warn("The address was not found");
                //     throw new RuntimeException("The address was not found" + address);

                // 異常終了の場合
                // } else {
                //     String errorMessage = jsonNode.has("error_message") 
                //         ? jsonNode.get("error_message").asText() 
                //         : "Unknown error";
                //     log.error("Geocoding API error - Status: {}, Message: {}", status, errorMessage);
                //     throw new RuntimeException("Geocoding API error: " + status + " - " + errorMessage);
                // }
            }

            JsonNode results = jsonNode.get("results");
            // 返却結果がnullまたは配列以外または空の場合、エラー
            if (results == null || !results.isArray() || results.isEmpty()) {
                return null;
                // log.warn("No results found for address: {}", address);
                // throw new RuntimeException("No results found for address: " + address);
            }

            // Geometryがnullの場合、エラー
            log.info("Geocoding results = {}", results);
            //JsonNode geometry = results.get(0).get("geometry");
            // if (geometry == null) {
            //     throw new RuntimeException("Invalid response structure: missing geometry");
            // }
            // if (geometry != null) {
            //     JsonNode location = geometry.get("location");
            //     double lat = location.get("lat").asDouble();
            //     double lng = location.get("lng").asDouble();
            //     log.debug("Successfully geocoded address: {} -> lat: {}, lng: {}", address, lat, lng);

            //     return new GeoPoint(lat, lng);
            // }

            for (JsonNode result : results) {
                if (result.path("partial_match").asBoolean()) {
                    continue;
                }

                boolean hasRoute = false;
                for (JsonNode type : result.path("types")) {
                    if (StringUtils.equals("route", type.asText())) {
                        hasRoute = true;
                    }
                }
                if (hasRoute) {
                    continue;
                }

                if (result.has("geometry")) {
                    JsonNode location = result.get("geometry").get("location");
                    double lat = location.get("lat").asDouble();
                    double lng = location.get("lng").asDouble();
                    log.debug("Successfully geocoded address: {} -> lat: {}, lng: {}", address, lat, lng);
                    return new GeoPoint(lat, lng);
                }
            }
            // 緯度経度が取得できない場合、エラー
            // if (location == null || !location.has("lat") || !location.has("lng")) {
            //     throw new RuntimeException("Invalid response structure: missing location coordinates");
            // }

            return null;

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
