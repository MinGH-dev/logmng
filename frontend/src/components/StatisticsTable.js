import React, { useMemo } from 'react';
import './StatisticsTable.css';

const StatisticsTable = ({ statisticsData, statisticsType, sortConfig, onSort }) => {
  const sortedData = useMemo(() => {
    if (!statisticsData || !statisticsData.dailyStats) return [];

    let data = [...statisticsData.dailyStats];

    if (sortConfig.key) {
      data.sort((a, b) => {
        let aValue = a[sortConfig.key];
        let bValue = b[sortConfig.key];

        // 사용자별 통계의 경우 필드명이 다름
        if (statisticsType === 'user') {
          if (sortConfig.key === 'totalSearches') aValue = a.searchCount || 0;
          if (sortConfig.key === 'totalSearches') bValue = b.searchCount || 0;
          if (sortConfig.key === 'totalDecrypts') aValue = a.decryptCount || 0;
          if (sortConfig.key === 'totalDecrypts') bValue = b.decryptCount || 0;
          if (sortConfig.key === 'totalLogins') aValue = a.loginCount || 0;
          if (sortConfig.key === 'totalLogins') bValue = b.loginCount || 0;
        }

        if (typeof aValue === 'string') {
          aValue = aValue.toLowerCase();
          bValue = bValue.toLowerCase();
        }

        if (aValue < bValue) {
          return sortConfig.direction === 'asc' ? -1 : 1;
        }
        if (aValue > bValue) {
          return sortConfig.direction === 'asc' ? 1 : -1;
        }
        return 0;
      });
    }

    return data;
  }, [statisticsData, sortConfig, statisticsType]);

  const handleSort = (key) => {
    onSort(key);
  };

  const getSortIcon = (key) => {
    if (sortConfig.key !== key) return '⇅';
    return sortConfig.direction === 'asc' ? '↑' : '↓';
  };

  if (!statisticsData) return null;

  return (
    <div className="statistics-table">
      <table>
        <thead>
          <tr>
            <th onClick={() => handleSort('date')}>
              날짜 {getSortIcon('date')}
            </th>
            <th onClick={() => handleSort('totalSearches')}>
              검색 횟수 {getSortIcon('totalSearches')}
            </th>
            <th onClick={() => handleSort('totalDecrypts')}>
              복호화 횟수 {getSortIcon('totalDecrypts')}
            </th>
            <th onClick={() => handleSort('totalLogins')}>
              로그인 횟수 {getSortIcon('totalLogins')}
            </th>
          </tr>
        </thead>
        <tbody>
          {sortedData.length === 0 ? (
            <tr>
              <td colSpan="4">데이터가 없습니다.</td>
            </tr>
          ) : (
            sortedData.map((stat, index) => (
              <tr key={index}>
                <td>{stat.date}</td>
                <td>{statisticsType === 'user' ? (stat.searchCount || 0) : (stat.totalSearches || 0)}</td>
                <td>{statisticsType === 'user' ? (stat.decryptCount || 0) : (stat.totalDecrypts || 0)}</td>
                <td>{statisticsType === 'user' ? (stat.loginCount || 0) : (stat.totalLogins || 0)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {statisticsData.summary && (
        <div className="summary">
          <h3>요약</h3>
          <div className="summary-items">
            <div>전체 검색 횟수: {statisticsData.summary.totalSearches || 0}</div>
            <div>전체 복호화 횟수: {statisticsData.summary.totalDecrypts || 0}</div>
            <div>전체 로그인 횟수: {statisticsData.summary.totalLogins || 0}</div>
            <div>활동 사용자 수: {statisticsData.summary.uniqueUsers || 0}</div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StatisticsTable;





