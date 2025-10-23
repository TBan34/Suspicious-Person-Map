// src/components/ReportList.jsx
import React from 'react';

function ReportList({ reports }) {
  if (!reports || reports.length === 0) {
    return <p>報告データがありません。</p>;
  }

  return (
    <div style={{ marginTop: '1rem' }}>
      <h2>報告一覧</h2>
      <table border="1" cellPadding="8" style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            <th>ID</th>
            <th>タイトル</th>
            <th>住所</th>
            <th>詳細</th>
            <th>登録日時</th>
          </tr>
        </thead>
        <tbody>
          {reports.map((r) => (
            <tr key={r.id}>
              <td>{r.id}</td>
              <td>{r.title}</td>
              <td>{r.address}</td>
              <td>{r.detail}</td>
              <td>{new Date(r.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default ReportList;
