package com.example.backend.service;

import com.example.backend.model.GeoPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google Maps API＞Geocoding APIを使用し、座標取得。
 */

@Service
public class GeocodeService {

    @Value("${google.api.key}")
    private String apiKey;

    public GeoPoint getLatLng(String address) {
        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                    URLEncoder.encode(address, StandardCharsets.UTF_8) +
                    "&key=" + apiKey;
            HttpResponse<String> response = Unirest.get(url).asString();
            JSONObject json = new JSONObject(response.getBody());
            JSONObject location = json.getJSONArray("results")
                                      .getJSONObject(0)
                                      .getJSONObject("geometry")
                                      .getJSONObject("location");
            double lat = location.getDouble("lat");
            double lng = location.getDouble("lng");
            return new GeoPoint(lat, lng);
        } catch (Exception e) {
            throw new RuntimeException("Geocoding failed for address: " + address, e);
        }
    }
}
