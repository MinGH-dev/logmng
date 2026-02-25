import React from 'react';
import './StatisticsHeader.css';

const StatisticsHeader = ({
  statisticsType,
  onTypeChange,
  startDate,
  endDate,
  onStartDateChange,
  onEndDateChange,
  year,
  month,
  onYearChange,
  onMonthChange
}) => {
  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 10 }, (_, i) => currentYear - 5 + i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);

  return (
    <div className="statistics-header">
      <div className="type-toggle">
        <button
          className={statisticsType === 'daily' ? 'active' : ''}
          onClick={() => onTypeChange('daily')}
        >
          일별 통계
        </button>
        <button
          className={statisticsType === 'monthly' ? 'active' : ''}
          onClick={() => onTypeChange('monthly')}
        >
          월별 통계
        </button>
      </div>

      <div className="date-selector">
        {statisticsType === 'daily' ? (
          <>
            <div className="date-selector-group">
              <label htmlFor="statistics-start-date">시작일</label>
              <input
                id="statistics-start-date"
                type="date"
                value={startDate}
                onChange={(e) => onStartDateChange(e.target.value)}
                aria-label="시작일"
              />
            </div>
            <div className="date-selector-group">
              <label htmlFor="statistics-end-date">종료일</label>
              <input
                id="statistics-end-date"
                type="date"
                value={endDate}
                onChange={(e) => onEndDateChange(e.target.value)}
                aria-label="종료일"
              />
            </div>
          </>
        ) : (
          <>
            <label>
              연도:
              <select value={year} onChange={(e) => onYearChange(parseInt(e.target.value, 10))}>
                {years.map(y => (
                  <option key={y} value={y}>{y}</option>
                ))}
              </select>
            </label>
            <label>
              월:
              <select value={month} onChange={(e) => onMonthChange(parseInt(e.target.value, 10))}>
                {months.map(m => (
                  <option key={m} value={m}>{m}월</option>
                ))}
              </select>
            </label>
          </>
        )}
      </div>
    </div>
  );
};

export default StatisticsHeader;

