package com.example.backend.service;

import com.example.backend.model.GeoPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google Maps API＞Geocoding APIを使用し、座標取得。
 */
@Service
@RequiredArgsConstructor
public class GeocodeService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.api.key}")
    private String apiKey;

    public GeoPoint getLatLng(String address) {
        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                    URLEncoder.encode(address, StandardCharsets.UTF_8) +
                    "&key=" + apiKey;
            
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            JsonNode results = jsonNode.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                throw new RuntimeException("No results found for address: " + address);
            }
            
            JsonNode location = results.get(0)
                    .get("geometry")
                    .get("location");
            
            double lat = location.get("lat").asDouble();
            double lng = location.get("lng").asDouble();
            return new GeoPoint(lat, lng);
        } catch (Exception e) {
            throw new RuntimeException("Geocoding failed for address: " + address, e);
        }
    }
}
