package com.example.backend.model;

public class GeoPoint {
    private final double latitude;
    private final double longitude;

    // 座標情報
    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // 緯度
    public double getLatitude() {
        return latitude;
    }

    // 経度
    public double getLongitude() {
        return longitude;
    }
}


