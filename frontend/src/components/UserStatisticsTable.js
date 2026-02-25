import React from 'react';
import './UserStatisticsTable.css';

/**
 * 행에서 집계 검색/복호화 건수 추출.
 * API가 searchCount/decryptCount를 주면 사용, 아니면 per-log-type 키(*SearchCount, *DecryptCount) 합산.
 */
function getSearchDecryptCounts(user) {
  if (user.searchCount !== undefined && user.decryptCount !== undefined) {
    return { searchCount: user.searchCount, decryptCount: user.decryptCount };
  }
  let searchCount = 0;
  let decryptCount = 0;
  Object.keys(user).forEach((key) => {
    if (key.endsWith('SearchCount')) searchCount += Number(user[key]) || 0;
    if (key.endsWith('DecryptCount')) decryptCount += Number(user[key]) || 0;
  });
  return { searchCount, decryptCount };
}

/**
 * 사용자별 통계 테이블.
 * API 응답: userId, userName, totalCount, loginCount, searchCount, decryptCount (또는 per-log-type 키)
 */
const UserStatisticsTable = ({ userStatistics, sortConfig, onSort }) => {
  if (!userStatistics || userStatistics.length === 0) {
    return (
      <div className="user-statistics-table-container">
        <div className="no-data-message">사용자별 통계 데이터가 없습니다.</div>
      </div>
    );
  }

  // 정렬 시 searchCount/decryptCount 사용 (정규화된 값)
  const normalized = userStatistics.map((u) => ({
    ...u,
    ...getSearchDecryptCounts(u)
  }));

  // 정렬된 데이터 생성
  const sortedData = [...normalized].sort((a, b) => {
    if (!sortConfig.key) return 0;

    const aValue = a[sortConfig.key];
    const bValue = b[sortConfig.key];

    if (typeof aValue === 'string') {
      return sortConfig.direction === 'asc'
        ? (aValue || '').localeCompare(bValue || '')
        : (bValue || '').localeCompare(aValue || '');
    }

    return sortConfig.direction === 'asc'
      ? (aValue || 0) - (bValue || 0)
      : (bValue || 0) - (aValue || 0);
  });

  const handleSort = (key) => {
    onSort(key);
  };

  const getSortIcon = (key) => {
    if (sortConfig.key !== key) {
      return <span className="sort-icon">⇅</span>;
    }
    return sortConfig.direction === 'asc'
      ? <span className="sort-icon">↑</span>
      : <span className="sort-icon">↓</span>;
  };

  return (
    <div className="user-statistics-table-container">
      <h3>사용자별 통계</h3>
      <div className="user-statistics-table-wrapper">
        <table className="user-statistics-table">
          <thead>
            <tr>
              <th onClick={() => handleSort('userId')} className="sortable">
                사용자 ID {getSortIcon('userId')}
              </th>
              <th onClick={() => handleSort('userName')} className="sortable">
                사용자명 {getSortIcon('userName')}
              </th>
              <th onClick={() => handleSort('totalCount')} className="sortable">
                전체 건수 {getSortIcon('totalCount')}
              </th>
              <th onClick={() => handleSort('loginCount')} className="sortable">
                로그인 {getSortIcon('loginCount')}
              </th>
              <th onClick={() => handleSort('searchCount')} className="sortable">
                검색 {getSortIcon('searchCount')}
              </th>
              <th onClick={() => handleSort('decryptCount')} className="sortable">
                복호화 {getSortIcon('decryptCount')}
              </th>
            </tr>
          </thead>
          <tbody>
            {sortedData.map((user, index) => (
              <tr key={user.userId || index}>
                <td className="user-id-cell">{user.userId || '-'}</td>
                <td className="user-name-cell">{user.userName || '-'}</td>
                <td className="count-cell">{user.totalCount ?? 0}</td>
                <td className="count-cell">{user.loginCount ?? 0}</td>
                <td className="count-cell">{user.searchCount ?? 0}</td>
                <td className="count-cell">{user.decryptCount ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default UserStatisticsTable;

