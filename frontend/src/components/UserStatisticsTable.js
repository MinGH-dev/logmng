import React from 'react';
import './UserStatisticsTable.css';

const UserStatisticsTable = ({ userStatistics, sortConfig, onSort, logTypeList }) => {
  if (!userStatistics || userStatistics.length === 0) {
    return (
      <div className="user-statistics-table-container">
        <div className="no-data-message">사용자별 통계 데이터가 없습니다.</div>
      </div>
    );
  }

  // SEARCH/DECRYPT 액션을 지원하는 로그 타입만 필터링 (LOGIN 제외)
  const searchDecryptLogTypes = (logTypeList || []).filter(lt => {
    if (lt.id === 'LOGIN') return false;
    const actions = Array.isArray(lt.action) ? lt.action : [lt.action];
    return actions.includes('SEARCH') || actions.includes('DECRYPT');
  });

  // 정렬된 데이터 생성
  const sortedData = [...userStatistics].sort((a, b) => {
    if (!sortConfig.key) return 0;
    
    const aValue = a[sortConfig.key];
    const bValue = b[sortConfig.key];
    
    if (typeof aValue === 'string') {
      return sortConfig.direction === 'asc' 
        ? aValue.localeCompare(bValue)
        : bValue.localeCompare(aValue);
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
              <th rowSpan="2" onClick={() => handleSort('userId')} className="sortable">
                사용자 ID {getSortIcon('userId')}
              </th>
              <th rowSpan="2" onClick={() => handleSort('userName')} className="sortable">
                사용자명 {getSortIcon('userName')}
              </th>
              <th rowSpan="2" onClick={() => handleSort('totalCount')} className="sortable">
                전체 건수 {getSortIcon('totalCount')}
              </th>
              <th rowSpan="2" onClick={() => handleSort('loginCount')} className="sortable">
                로그인 {getSortIcon('loginCount')}
              </th>
              {searchDecryptLogTypes.map(lt => (
                <th key={lt.id} colSpan="2" className="log-type-header">
                  {lt.displayName || lt.name}
                </th>
              ))}
            </tr>
            <tr>
              {searchDecryptLogTypes.map(lt => (
                <React.Fragment key={lt.id}>
                  <th 
                    onClick={() => handleSort(`${lt.id}SearchCount`)} 
                    className="sortable"
                  >
                    검색 {getSortIcon(`${lt.id}SearchCount`)}
                  </th>
                  <th 
                    onClick={() => handleSort(`${lt.id}DecryptCount`)} 
                    className="sortable"
                  >
                    복호화 {getSortIcon(`${lt.id}DecryptCount`)}
                  </th>
                </React.Fragment>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedData.map((user, index) => (
              <tr key={user.userId || index}>
                <td className="user-id-cell">{user.userId || '-'}</td>
                <td className="user-name-cell">{user.userName || '-'}</td>
                <td className="count-cell">{user.totalCount || 0}</td>
                <td className="count-cell">{user.loginCount || 0}</td>
                {searchDecryptLogTypes.map(lt => (
                  <React.Fragment key={lt.id}>
                    <td className="count-cell">
                      {user[`${lt.id}SearchCount`] || 0}
                    </td>
                    <td className="count-cell">
                      {user[`${lt.id}DecryptCount`] || 0}
                    </td>
                  </React.Fragment>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default UserStatisticsTable;

