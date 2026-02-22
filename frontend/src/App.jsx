import React, { useEffect, useState } from 'react';
import { getReports } from './services/api';
import AddressList from './components/AddressList';
import MapView from './components/MapView';
import ReportDetail from './components/ReportDetail';
import './App.css';
import logo from "./assets/SuspiciousPersonMapLogo.png";

function App() {
  const [reports, setReports] = useState([]);
  const [selectedReport, setSelectedReport] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchReports = async () => {
      try {
        const data = await getReports();
        setReports(data);
      } catch (error) {
        console.error('Failed to fetch reports:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchReports();
  }, []);

  const handleReportSelect = (report) => {
    setSelectedReport(report);
  };

  const handleMarkerClick = (report) => {
    setSelectedReport(report);
  };

  return (
    <div className="app">
      <header className="app-header">
        <img src={logo} alt="ロゴ" className="header-logo" />
        <h1>福岡市不審者マップ</h1>
      </header>

      <main className="app-main">
        <aside className="sidebar-left">
          <AddressList 
            reports={reports} 
            onSelect={handleReportSelect}
            selectedReport={selectedReport}
          />
        </aside>

        <section className="map-section">
          {loading ? (
            <div className="loading">読み込み中...</div>
          ) : (
            <MapView 
              reports={reports}
              onMarkerClick={handleMarkerClick}
              selectedReport={selectedReport}
            />
          )}
        </section>

        <aside className="sidebar-right">
          <ReportDetail report={selectedReport} />
        </aside>
      </main>

      <footer className="app-footer">
        <h2>©TBan34</h2>
      </footer>
    </div>
  );
}

export default App;
