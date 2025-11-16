import React from 'react';
import './ReportDetail.css';

function ReportDetail({ report }) {
  if (!report) {
    return (
      <div className="report-detail">
        <h2 className="report-detail-title">不審者情報の詳細</h2>
        <div className="report-detail-empty">
          <p>GoogleMapのピンをクリックすることで、</p>
          <p>発生場所と概要をここに表示します</p>
        </div>
      </div>
    );
  }

  const formatDate = (dateString) => {
    if (!dateString) return '日時不明';
    try {
      const date = new Date(dateString);
      return date.toLocaleString('ja-JP', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch (e) {
      return dateString;
    }
  };

  const buildAddress = () => {
    const parts = [
      report.prefecture,
      report.municipality,
      report.district,
      report.addressDetails,
    ].filter(Boolean);

    return parts.length > 0 ? parts.join(' ') : '住所未設定';
  };

  return (
    <div className="report-detail">
      <h2 className="report-detail-title">不審者情報の詳細</h2>

      <div className="report-detail-content">
        <div className="report-detail-section">
          <h3 className="report-detail-section-title">発生場所</h3>
          <p className="report-detail-address">{buildAddress()}</p>
          {report.prefecture && report.municipality && (
            <div className="report-detail-location">
              <span className="report-detail-location-item">
                {report.prefecture}
              </span>
              <span className="report-detail-location-item">
                {report.municipality}
              </span>
              {report.district && (
                <span className="report-detail-location-item">
                  {report.district}
                </span>
              )}
            </div>
          )}
        </div>

        {report.summary && (
          <div className="report-detail-section">
            <h3 className="report-detail-section-title">概要</h3>
            <p className="report-detail-summary">{report.summary}</p>
          </div>
        )}

        {report.tag && (
          <div className="report-detail-section">
            <h3 className="report-detail-section-title">タグ</h3>
            <span className="report-detail-tag">{report.tag}</span>
          </div>
        )}

        <div className="report-detail-section">
          <h3 className="report-detail-section-title">座標</h3>
          <div className="report-detail-coordinates">
            <div className="report-detail-coordinate-item">
              <span className="report-detail-coordinate-label">緯度:</span>
              <span className="report-detail-coordinate-value">
                {report.latitude.toFixed(6)}
              </span>
            </div>
            <div className="report-detail-coordinate-item">
              <span className="report-detail-coordinate-label">経度:</span>
              <span className="report-detail-coordinate-value">
                {report.longitude.toFixed(6)}
              </span>
            </div>
          </div>
        </div>

        {report.created && (
          <div className="report-detail-section">
            <h3 className="report-detail-section-title">登録日時</h3>
            <p className="report-detail-date">{formatDate(report.created)}</p>
          </div>
        )}

        {report.userId && (
          <div className="report-detail-section">
            <h3 className="report-detail-section-title">報告者ID</h3>
            <p className="report-detail-user-id">{report.userId}</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default ReportDetail;

