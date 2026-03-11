import React from 'react';
import UserContextFilterBlock from './common/UserContextFilterBlock';
import './StatisticsFilters.css';

const StatisticsFilters = ({
  filters,
  onFiltersChange,
  onSearch,
  onReset,
  loading = false,
  userList,
  departmentList,
  ipList,
  logTypeList,
  hideUserFilters = false,
}) => {
  const handleFilterChange = (key, value) => {
    onFiltersChange({
      ...filters,
      [key]: value
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch?.();
  };

  const handleResetClick = (e) => {
    e.preventDefault();
    onReset?.();
  };

  // 로그 타입 목록: '전체' + 로그인(LOGIN) + API 목록. 백엔드 '전체' = LOGIN + API 목록 합산과 동일하게 맞춤.
  const logTypes = [
    { value: '', label: '전체' },
    { value: 'LOGIN', label: '로그인' },
    ...(logTypeList || []).map(lt => ({
      value: lt.id,
      label: lt.displayName || lt.name
    }))
  ];

  return (
    <div className="statistics-filters">
      <div className="statistics-filters__header">
        <h3>검색 조건</h3>
      </div>
      <form
        className="statistics-filters__form"
        onSubmit={handleSubmit}
        aria-label="통계 검색 조건"
      >
        {/* Group titles above fields per forms-and-filters.md § Filter group title placement; Search/Reset in actions row per UX-REDESIGN */}
        <div className="statistics-filters__body">
          {/* Row 1: 로그 타입 + UserContextFilterBlock (1–2 row layout) */}
          <div className="statistics-filters__row-1">
            <div className="statistics-filters__block statistics-filters__block--log-type" role="group" aria-labelledby="statistics-filter-log-type-heading">
              <h4 id="statistics-filter-log-type-heading" className="statistics-filters__block-heading">로그 타입</h4>
              <div className="form-group">
                <select
                  id="statistics-filter-logType"
                  value={filters.logType || ''}
                  onChange={(e) => handleFilterChange('logType', e.target.value)}
                  className="form-control"
                  aria-label="로그 타입"
                >
                  {logTypes.map(logType => (
                    <option key={logType.value} value={logType.value}>
                      {logType.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <UserContextFilterBlock
              blockLabel="사용자"
              hideUserFilters={hideUserFilters}
              departmentList={departmentList}
              userList={userList}
              values={{
                department: filters.department || '',
                username: filters.username || '',
                userId: filters.userId || '',
              }}
              onChange={(name, value) => handleFilterChange(name, value)}
              idPrefix="statistics-filter"
              compact
            />
          </div>

          {/* Row 2: 기타 조건 (IP, title above fields) + 검색 + 초기화 — aligned with activity log (role="group", aria-labelledby) */}
          <div className="statistics-filters__row-2">
            <div className="statistics-filters__row-2-fields">
              {!hideUserFilters && (
                <div className="statistics-filters__extra" role="group" aria-labelledby="statistics-filter-extra-heading">
                  <h4 id="statistics-filter-extra-heading" className="statistics-filters__extra-heading">기타 조건</h4>
                  <div className="statistics-filters__extra-fields">
                    <div className="form-group">
                      <label htmlFor="statistics-filter-ip">IP</label>
                      <select
                        id="statistics-filter-ip"
                        value={filters.ip || ''}
                        onChange={(e) => handleFilterChange('ip', e.target.value)}
                        className="form-control"
                        aria-label="IP"
                      >
                        <option value="">전체</option>
                        {(ipList || []).map(ip => (
                          <option key={ip} value={ip}>{ip}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                </div>
              )}
            </div>
            <div className="statistics-filters__actions">
              <button type="submit" className="btn btn-primary" disabled={loading}>
                {loading ? '검색 중...' : '검색'}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleResetClick}>
                초기화
              </button>
            </div>
          </div>
        </div>
      </form>
    </div>
  );
};

export default StatisticsFilters;

