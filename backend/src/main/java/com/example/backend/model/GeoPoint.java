package com.example.backend.model;

public class GeoPoint {
    private final double latitude;
    private final double longitude;

    /**
     * 緯度と経度を保持する座標情報を生成する。
     * レスポンス: 指定された緯度・経度を持つ GeoPoint インスタンス。
     *
     * @param latitude 緯度
     * @param longitude 経度
     */
    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * 緯度を取得する。
     *
     * @return 緯度
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * 経度を取得する。
     *
     * @return 経度
     */
    public double getLongitude() {
        return longitude;
    }
}

