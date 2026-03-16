import React from 'react';
import UserContextFilterBlock from './common/UserContextFilterBlock';
import './StatisticsFilters.css';

/** Default error id for date range (start ≤ end); must match ActivityStatistics error element id for aria-describedby (date-search.md). */
const DATE_RANGE_ERROR_ID = 'activity-statistics-date-range-error';

const StatisticsFilters = ({
  filters,
  onFiltersChange,
  onSearch,
  onReset,
  loading = false,
  userList,
  departmentList,
  ipList, // unused: IP is text input per search-fields-by-screen.md §3 (activity-log alignment)
  logTypeList,
  isSelfScope = false,
  selfContext = null,
  // Form per mode (req 20260313): date/period block inside form; content differs by statisticsType
  statisticsType = 'daily',
  startDate = '',
  endDate = '',
  onStartDateChange,
  onEndDateChange,
  year,
  month,
  onYearChange,
  onMonthChange,
  dateRangeInvalid = false,
  dateRangeErrorId = DATE_RANGE_ERROR_ID,
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

  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 10 }, (_, i) => currentYear - 5 + i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);

  return (
    <div className="statistics-filters">
      <div className="statistics-filters__header">
        <h3>검색 조건</h3>
      </div>
      <form
        className="statistics-filters__form sf-compact-panel"
        onSubmit={handleSubmit}
        aria-label="통계 검색 조건"
      >
        {/* Form per mode (req 20260313): date/period block — daily (시작일/종료일) or monthly (연도/월) */}
        <div className="statistics-filters__body">
          <div className="statistics-filters__date-row" role="group" aria-labelledby="statistics-filters-date-heading">
            <h4 id="statistics-filters-date-heading" className="statistics-filters__block-heading">
              {statisticsType === 'daily' ? '기간 (일별)' : '기간 (월별)'}
            </h4>
            {statisticsType === 'daily' ? (
              <div className="statistics-filters__date-fields">
                <div className="form-group">
                  <label htmlFor="statistics-filter-start-date">시작일</label>
                  <input
                    id="statistics-filter-start-date"
                    type="date"
                    value={startDate}
                    onChange={(e) => onStartDateChange?.(e.target.value)}
                    className="form-control"
                    aria-label="시작일"
                    aria-invalid={!!dateRangeInvalid}
                    aria-describedby={dateRangeInvalid ? dateRangeErrorId : undefined}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="statistics-filter-end-date">종료일</label>
                  <input
                    id="statistics-filter-end-date"
                    type="date"
                    value={endDate}
                    onChange={(e) => onEndDateChange?.(e.target.value)}
                    className="form-control"
                    aria-label="종료일"
                    aria-invalid={!!dateRangeInvalid}
                    aria-describedby={dateRangeInvalid ? dateRangeErrorId : undefined}
                  />
                </div>
              </div>
            ) : (
              <div className="statistics-filters__date-fields">
                <div className="form-group">
                  <label htmlFor="statistics-filter-year">연도</label>
                  <select
                    id="statistics-filter-year"
                    value={year ?? ''}
                    onChange={(e) => onYearChange?.(parseInt(e.target.value, 10))}
                    className="form-control"
                    aria-label="연도"
                  >
                    {years.map(y => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="statistics-filter-month">월</label>
                  <select
                    id="statistics-filter-month"
                    value={month ?? ''}
                    onChange={(e) => onMonthChange?.(parseInt(e.target.value, 10))}
                    className="form-control"
                    aria-label="월"
                  >
                    {months.map(m => (
                      <option key={m} value={m}>{m}월</option>
                    ))}
                  </select>
                </div>
              </div>
            )}
          </div>

          {/* Single row (non-date): 로그 타입 + 사용자 블록 + 기타 조건 + 검색/초기화 (forms-and-filters.md § Single row for non-date; search-fields-by-screen.md §3) */}
          <div className="statistics-filters__row-non-date">
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
              mode={isSelfScope ? 'locked' : 'editable'}
              departmentList={departmentList}
              userList={userList}
              values={{
                department: filters.department || '',
                username: filters.username || '',
                userId: filters.userId || '',
              }}
              lockedValues={selfContext || undefined}
              onChange={(name, value) => handleFilterChange(name, value)}
              idPrefix="statistics-filter"
              compact
              usernameMaxLength={5}
            />
            {/* 기타 조건: scope=self에서도 표시 (req 20260316) */}
            <div className="statistics-filters__extra" role="group" aria-labelledby="statistics-filter-extra-heading">
              <h4 id="statistics-filter-extra-heading" className="statistics-filters__extra-heading">기타 조건</h4>
              <div className="statistics-filters__extra-fields">
                <div className="form-group">
                  <label htmlFor="statistics-filter-ip">IP 주소</label>
                  <input
                    type="text"
                    id="statistics-filter-ip"
                    value={filters.ip || ''}
                    onChange={(e) => handleFilterChange('ip', e.target.value)}
                    className="form-control"
                    placeholder="IP 주소"
                    aria-label="IP 주소"
                  />
                </div>
              </div>
            </div>
            <div className="statistics-filters__actions-row" role="group" aria-label="필터 액션">
              <button type="submit" className="btn btn-primary sf-btn" disabled={loading}>
                {loading ? '검색 중...' : '검색'}
              </button>
              <button type="button" className="btn btn-secondary sf-btn" onClick={handleResetClick}>
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

