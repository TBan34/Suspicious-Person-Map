import React, { useCallback, useMemo } from 'react';
import { GoogleMap, Marker, useLoadScript } from '@react-google-maps/api';
import './MapView.css';

// 福岡県福岡市の座標（デフォルト表示）
const DEFAULT_CENTER = {
  lat: 33.5904,
  lng: 130.4017,
};

const DEFAULT_ZOOM = 13;

// 赤色のピンアイコンのURL（Google Mapsのデフォルト赤ピン）
const RED_MARKER_ICON = {
  url: 'http://maps.google.com/mapfiles/ms/icons/red-dot.png',
  scaledSize: { width: 32, height: 32 },
};

const mapContainerStyle = {
  width: '100%',
  height: '100%',
};

function MapView({ reports, onMarkerClick, selectedReport }) {
  const { isLoaded, loadError } = useLoadScript({
    googleMapsApiKey: import.meta.env.VITE_GOOGLE_MAPS_API_KEY || '',
  });

  const mapOptions = useMemo(
    () => ({
      disableDefaultUI: false,
      zoomControl: true,
      streetViewControl: false,
      mapTypeControl: true,
      fullscreenControl: true,
      styles: [
        {
          featureType: 'all',
          elementType: 'geometry',
          stylers: [{ color: '#242424' }],
        },
        {
          featureType: 'all',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#e0e0e0' }],
        },
        {
          featureType: 'water',
          elementType: 'geometry',
          stylers: [{ color: '#1a1a1a' }],
        },
        {
          featureType: 'road',
          elementType: 'geometry',
          stylers: [{ color: '#2d2d2d' }],
        },
      ],
    }),
    []
  );

  const handleMarkerClick = useCallback(
    (report) => {
      if (onMarkerClick) {
        onMarkerClick(report);
      }
    },
    [onMarkerClick]
  );

  if (loadError) {
    return (
      <div className="map-error">
        <p>地図の読み込みに失敗しました</p>
        <p className="map-error-detail">
          Google Maps APIキーを設定してください
        </p>
      </div>
    );
  }

  if (!isLoaded) {
    return (
      <div className="map-loading">
        <div className="map-loading-spinner"></div>
        <p>地図を読み込み中...</p>
      </div>
    );
  }

  return (
    <div className="map-view">
      <GoogleMap
        mapContainerStyle={mapContainerStyle}
        center={DEFAULT_CENTER}
        zoom={DEFAULT_ZOOM}
        options={mapOptions}
      >
        {reports &&
          reports.map((report) => {
            if (!report.latitude || !report.longitude) return null;

            const isSelected = selectedReport?.id === report.id;

            return (
              <Marker
                key={report.id}
                position={{
                  lat: report.latitude,
                  lng: report.longitude,
                }}
                icon={RED_MARKER_ICON}
                onClick={() => handleMarkerClick(report)}
                title={
                  report.summary
                    ? `${report.prefecture || ''}${report.municipality || ''} - ${report.summary}`
                    : `${report.prefecture || ''}${report.municipality || ''}`
                }
                animation={isSelected ? window.google?.maps?.Animation?.BOUNCE : null}
              />
            );
          })}
      </GoogleMap>

      <div className="map-info">
        <p className="map-info-item">※1. 赤色のピンで表示</p>
        <p className="map-info-item">
          ※2. ユーザーはマウス操作等でMapの移動が可能
        </p>
        <p className="map-info-item">※3. デフォルト表示は福岡県福岡市</p>
      </div>
    </div>
  );
}

export default MapView;
