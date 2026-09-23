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
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.api.key}")
    private String apiKey;

    /**
     * 住所を段階的に簡略化しながら Geocoding API を呼び出し、座標情報を取得する。
     *
     * @param address 座標へ変換する住所
     * @return 完全一致した住所の座標情報
     */
    public GeoPoint getLatLng(String address) {
        List<String> fallbackAddresses = createFallbackAddresses(address);

        for (String fallbackAddress : fallbackAddresses) {
            GeoPoint point = callGeocodingApi(fallbackAddress);
            if (point != null) {
                log.debug("Geocoding APIの実行に成功しました: {}", fallbackAddress);
                return point;
            }
        }

        throw new RuntimeException("有効な住所情報が見つかりませんでした: " + address);
    }

    /**
     * Geocoding API の再試行に使用する、段階的に簡略化した住所候補を生成する。
     *
     * @param address 簡略化の基準となる住所
     * @return 重複を除いた住所候補の一覧
     */
    private List<String> createFallbackAddresses(String address) {
    
        // TODO: 住所情報（フォールバック用）の精度向上
        // 末尾ハイフンの場合、削除
        if (StringUtils.equals("-", address.substring(address.length() -1))) {
            address = StringUtils.removeEnd(address, "-");
        }

        List<String> fallbackAddresses = new ArrayList<>();
        fallbackAddresses.add(address);

        // 丁目まで
        fallbackAddresses.add(address.replaceAll("-\\d+$", ""));
        fallbackAddresses.add(address.replaceAll("丁目.*$", "丁目"));

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

    /**
     * 指定された住所で Geocoding API を呼び出し、完全一致した座標を抽出する。
     *
     * @param address Geocoding API に渡す住所
     * @return 完全一致した座標情報。取得できない場合は null
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
            log.debug("Geocoding API呼出し住所: {}", address);
            log.debug("Geocoding リクエストURI = {}", uri);

            // Geocoding API実行
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // APIのステータスチェック
            String status = jsonNode.get("status").asText();
            // 異常終了または返却結果なしの場合
            if (!StringUtils.equals(GEOCODING_API.RESPONSE_STATUS.OK, status)) {

                String errorMessage = jsonNode.has("error_message")
                    ? jsonNode.get("error_message").asText()
                    : "no error_message";

                log.debug("Geocoding APIが異常終了または返却結果なし. status={}, error_message={}, address={}",
                    status, errorMessage, address);

                return null;
            }

            JsonNode results = jsonNode.get("results");
            log.debug("Geocoding results = {}", results);

            for (JsonNode result : results) {
                // 曖昧な情報の場合、次の取得結果へ
                if (result.path("partial_match").asBoolean()) {
                    continue;
                }

                // 道路情報の場合、次の取得結果へ
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
                    return new GeoPoint(lat, lng);
                }
            }

            return null;

        // RestTemplateの通信エラー
        } catch (RestClientException e) {
            throw new RuntimeException("RestTemplateの通信エラーが発生しました: " + address, e);
        
        // 上記以外のエラー
        } catch (Exception e) {
            throw new RuntimeException("Geocoding APIの呼び出しに失敗しました: " + address, e);
        }
    }

}
