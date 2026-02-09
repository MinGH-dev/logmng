import React from 'react';
import StatisticsChart from './StatisticsChart';
import StatisticsTable from './StatisticsTable';
import './StatisticsView.css';

const StatisticsView = ({
  statisticsData,
  statisticsType,
  viewType,
  onViewTypeChange,
  onExport,
  sortConfig,
  onSort
}) => {
  return (
    <div className="statistics-view">
      {viewType === 'chart' ? (
        <StatisticsChart
          statisticsData={statisticsData}
          statisticsType={statisticsType}
        />
      ) : (
        <StatisticsTable
          statisticsData={statisticsData}
          statisticsType={statisticsType}
          sortConfig={sortConfig}
          onSort={onSort}
        />
      )}
    </div>
  );
};

export default StatisticsView;

