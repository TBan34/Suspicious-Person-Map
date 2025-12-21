import React, { useMemo } from 'react';
import './AddressList.css';

function AddressList({ reports, onSelect, selectedReport }) {
  // 都道府県+市区町村でグループ化
  const groupedReports = useMemo(() => {
    const groups = {};
    reports.forEach((report) => {
      const prefecture = report.prefecture || '未設定';
      const municipality = report.municipality || '未設定';
      const key = `${prefecture}${municipality}`;
      
      if (!groups[key]) {
        groups[key] = {
          prefecture,
          municipality,
          reports: [],
        };
      }
      groups[key].reports.push(report);
    });
    return Object.values(groups);
  }, [reports]);

  if (!reports || reports.length === 0) {
    return (
      <div className="address-list">
        <h2 className="address-list-title">住所一覧</h2>
        <div className="address-list-empty">データがありません</div>
      </div>
    );
  }

  return (
    <div className="address-list">
      <h2 className="address-list-title">住所一覧</h2>
      
      <div className="address-list-content">
        {groupedReports.map((group, index) => (
          <div key={index} className="address-group">
            <div className="address-group-header">
              {group.prefecture}{group.municipality}
            </div>
            <div className="address-group-items">
              {group.reports.map((report) => {
                const address = [
                  report.prefecture,
                  report.municipality,
                  report.district,
                  report.addressDetails,
                ]
                  .filter(Boolean)
                  .join(' ') || '住所未設定';
                
                const isSelected = selectedReport?.id === report.id;
                
                return (
                  <div
                    key={report.id}
                    className={`address-item ${isSelected ? 'selected' : ''}`}
                    onClick={() => onSelect(report)}
                  >
                    <div className="address-item-text">{address}</div>
                    {report.summary && (
                      <div className="address-item-summary">
                        {report.summary.length > 30
                          ? `${report.summary.substring(0, 30)}...`
                          : report.summary}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default AddressList;

