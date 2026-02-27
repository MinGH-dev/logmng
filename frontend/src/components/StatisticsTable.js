import React, { useMemo } from 'react';
import DataTable, { EmptyTableBody } from './DataTable';
import './StatisticsTable.css';

const STAT_COLUMNS = [
  { key: 'date', label: '날짜', sortable: true },
  { key: 'totalSearches', label: '검색 횟수', sortable: true },
  { key: 'totalDecrypts', label: '복호화 횟수', sortable: true },
  { key: 'totalLogins', label: '로그인 횟수', sortable: true },
];

const StatisticsTable = ({ statisticsData, statisticsType, sortConfig, onSort }) => {
  const sortedData = useMemo(() => {
    if (!statisticsData || !statisticsData.dailyStats) return [];
    let data = [...statisticsData.dailyStats];
    if (sortConfig.key) {
      data.sort((a, b) => {
        let aValue = a[sortConfig.key];
        let bValue = b[sortConfig.key];
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
        if (aValue < bValue) return sortConfig.direction === 'asc' ? -1 : 1;
        if (aValue > bValue) return sortConfig.direction === 'asc' ? 1 : -1;
        return 0;
      });
    }
    return data;
  }, [statisticsData, sortConfig, statisticsType]);

  if (!statisticsData) return null;

  const hasData = sortedData.length > 0;

  return (
    <div className="statistics-table">
      <DataTable
        columns={STAT_COLUMNS}
        sortConfig={sortConfig.key ? sortConfig : null}
        onSort={onSort}
        emptyMessage="데이터가 없습니다."
        emptyColSpan={4}
        ariaLabel="일별 통계"
      >
        {!hasData ? (
          <EmptyTableBody colSpan={4} message="데이터가 없습니다." />
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
      </DataTable>

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
