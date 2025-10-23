// src/services/api.js

const BASE_URL = 'http://localhost:8080'; // Spring BootのAPIサーバーURL

// 全レポート取得
export async function getReports() {
  const response = await fetch(`${BASE_URL}/api/reports`);
  if (!response.ok) {
    throw new Error('Failed to fetch reports');
  }
  return await response.json();
}

// 特定レポート追加などが必要な場合（将来的に）
export async function addReport(data) {
  const response = await fetch(`${BASE_URL}/api/reports`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error('Failed to add report');
  }
  return await response.json();
}
