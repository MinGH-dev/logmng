import React from 'react';
import './StatisticsHeader.css';

/**
 * Type toggle only (일별/월별). Date/period inputs live in StatisticsFilters for form-per-mode (req 20260313; docs/design/forms-and-filters.md § Form per mode).
 */
const StatisticsHeader = ({
  statisticsType,
  onTypeChange,
}) => {
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
    </div>
  );
};

export default StatisticsHeader;

