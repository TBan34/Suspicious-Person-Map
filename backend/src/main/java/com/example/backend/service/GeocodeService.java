package com.example.backend.service;

import com.example.backend.model.GeoPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google Maps API＞Geocoding APIを使用し、座標取得。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodeService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.api.key}")
    private String apiKey;

    public GeoPoint getLatLng(String address) {
        if (address == null || address.trim().isEmpty()) {
            log.warn("Empty address provided for geocoding");
            throw new IllegalArgumentException("Address cannot be empty");
        }

        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                    URLEncoder.encode(address, StandardCharsets.UTF_8) +
                    "&key=" + apiKey;
            
            log.debug("Calling Geocoding API for address: {}", address);
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null) {
                throw new RuntimeException("Geocoding API returned null response");
            }
            
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // APIエラーステータスをチェック
            String status = jsonNode.get("status").asText();
            if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
                String errorMessage = jsonNode.has("error_message") 
                    ? jsonNode.get("error_message").asText() 
                    : "Unknown error";
                log.error("Geocoding API error - Status: {}, Message: {}", status, errorMessage);
                throw new RuntimeException("Geocoding API error: " + status + " - " + errorMessage);
            }
            
            JsonNode results = jsonNode.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                log.warn("No results found for address: {}", address);
                throw new RuntimeException("No results found for address: " + address);
            }
            
            JsonNode geometry = results.get(0).get("geometry");
            if (geometry == null) {
                throw new RuntimeException("Invalid response structure: missing geometry");
            }
            
            JsonNode location = geometry.get("location");
            if (location == null || !location.has("lat") || !location.has("lng")) {
                throw new RuntimeException("Invalid response structure: missing location coordinates");
            }
            
            double lat = location.get("lat").asDouble();
            double lng = location.get("lng").asDouble();
            
            log.debug("Successfully geocoded address: {} -> lat: {}, lng: {}", address, lat, lng);
            return new GeoPoint(lat, lng);
        } catch (RestClientException e) {
            log.error("Failed to call Geocoding API for address: {}", address, e);
            throw new RuntimeException("Failed to call Geocoding API for address: " + address, e);
        } catch (Exception e) {
            log.error("Geocoding failed for address: {}", address, e);
            throw new RuntimeException("Geocoding failed for address: " + address, e);
        }
    }
}
