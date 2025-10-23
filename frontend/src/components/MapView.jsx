import React from 'react';
import { GoogleMap, Marker, useLoadScript } from '@react-google-maps/api';

function MapView({ reports }) {
  const { isLoaded } = useLoadScript({
    googleMapsApiKey: import.meta.env.VITE_GOOGLE_MAPS_API_KEY,
  });

  if (!isLoaded) return <div>Loading Map...</div>;

  return (
    <GoogleMap
      mapContainerStyle={{ width: '100%', height: '600px' }}
      center={{ lat: 35.68, lng: 139.76 }} // 東京中心
      zoom={12}
    >
      {reports.map((r) => (
        <Marker key={r.id} position={{ lat: r.latitude, lng: r.longitude }} title={r.title} />
      ))}
    </GoogleMap>
  );
}

export default MapView;
