import React from 'react';
import './StatisticsFilters.css';

const StatisticsFilters = ({
  filters,
  onFiltersChange,
  userList,
  departmentList,
  ipList,
  logTypeList
}) => {
  const handleFilterChange = (key, value) => {
    onFiltersChange({
      ...filters,
      [key]: value
    });
  };

  // 로그 타입 목록 (동적)
  const logTypes = [
    { value: '', label: '전체' },
    ...(logTypeList || []).map(lt => ({
      value: lt.id,
      label: lt.displayName || lt.name
    }))
  ];

  return (
    <div className="statistics-filters">
      <h3>검색 조건</h3>
      <div className="filter-row">
        <label>
          로그 타입:
          <select
            value={filters.logType || ''}
            onChange={(e) => handleFilterChange('logType', e.target.value)}
          >
            {logTypes.map(logType => (
              <option key={logType.value} value={logType.value}>
                {logType.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          사용자 ID:
          <select
            value={filters.userId || ''}
            onChange={(e) => handleFilterChange('userId', e.target.value)}
          >
            <option value="">전체</option>
            {userList.map(user => (
              <option key={user.userId} value={user.userId}>
                {user.userId}
              </option>
            ))}
          </select>
        </label>

        <label>
          부서:
          <select
            value={filters.department || ''}
            onChange={(e) => handleFilterChange('department', e.target.value)}
          >
            <option value="">전체</option>
            {departmentList.map(dept => (
              <option key={dept} value={dept}>{dept}</option>
            ))}
          </select>
        </label>

        <label>
          IP:
          <select
            value={filters.ip || ''}
            onChange={(e) => handleFilterChange('ip', e.target.value)}
          >
            <option value="">전체</option>
            {ipList.map(ip => (
              <option key={ip} value={ip}>{ip}</option>
            ))}
          </select>
        </label>
      </div>
    </div>
  );
};

export default StatisticsFilters;

