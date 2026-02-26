import React from 'react';
import DataTable, { EmptyTableBody } from './DataTable';
import './UserStatisticsTable.css';

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

const USER_STAT_COLUMNS = [
  { key: 'userId', label: '사용자 ID', sortable: true },
  { key: 'userName', label: '사용자명', sortable: true },
  { key: 'totalCount', label: '전체 건수', sortable: true },
  { key: 'loginCount', label: '로그인', sortable: true },
  { key: 'searchCount', label: '검색', sortable: true },
  { key: 'decryptCount', label: '복호화', sortable: true },
];

const UserStatisticsTable = ({ userStatistics, sortConfig, onSort }) => {
  if (!userStatistics || userStatistics.length === 0) {
    return (
      <div className="user-statistics-table-container">
        <h3>사용자별 통계</h3>
        <DataTable
          columns={USER_STAT_COLUMNS}
          emptyMessage="사용자별 통계 데이터가 없습니다."
          emptyColSpan={USER_STAT_COLUMNS.length}
          ariaLabel="사용자별 통계"
        >
          <EmptyTableBody colSpan={USER_STAT_COLUMNS.length} message="사용자별 통계 데이터가 없습니다." />
        </DataTable>
      </div>
    );
  }

  const normalized = userStatistics.map((u) => ({
    ...u,
    ...getSearchDecryptCounts(u),
  }));

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

  return (
    <div className="user-statistics-table-container">
      <h3>사용자별 통계</h3>
      <DataTable
        columns={USER_STAT_COLUMNS}
        sortConfig={sortConfig.key ? sortConfig : null}
        onSort={onSort}
        emptyColSpan={USER_STAT_COLUMNS.length}
        ariaLabel="사용자별 통계"
      >
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
      </DataTable>
    </div>
  );
};

export default UserStatisticsTable;
