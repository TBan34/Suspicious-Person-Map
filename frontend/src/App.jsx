import React, { useEffect, useState } from 'react';
import MapView from './components/MapView';
import { getReports } from './services/api';

function App() {
  const [reports, setReports] = useState([]);

  useEffect(() => {
    getReports().then(setReports);
  }, []);

  return (
    <div>
      <h1>地域報告マップ</h1>
      <MapView reports={reports} />
    </div>
  );
}

export default App;
