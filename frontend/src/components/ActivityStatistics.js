import React, { useState, useEffect, useMemo } from 'react';
import { statisticsApi, logTypeApi } from '../services/api';
import { format } from 'date-fns';
import StatisticsHeader from './StatisticsHeader';
import StatisticsFilters from './StatisticsFilters';
import StatisticsView from './StatisticsView';
import UserStatisticsTable from './UserStatisticsTable';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../services/filterOptionsService';
import { getEmployeeNumberDisplay, getSelfContextForDisplay } from '../utils/security';
import './ActivityStatistics.css';

const getLockedSelfFilters = (selfContext) => ({
  userId: getEmployeeNumberDisplay(selfContext),
  username: selfContext?.username || '',
  department: selfContext?.department || '',
});

const ActivityStatistics = ({ user }) => {
  const isSelfScope = !user?.isSystemAdmin && user?.screenScopes?.statistics === 'self';
  const selfContext = useMemo(() => getSelfContextForDisplay(user), [user]);
  const lockedSelfFilters = getLockedSelfFilters(selfContext);

  // 통계 타입: 'daily' 또는 'monthly'
  const [statisticsType, setStatisticsType] = useState('daily');
  
  // 검색 조건
  const [filters, setFilters] = useState({
    logType: '',
    ...getLockedSelfFilters(isSelfScope ? selfContext : null),
    ip: ''
  });
  
  // 날짜 범위 (일별)
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  
  // 연도/월 (월별)
  const [year, setYear] = useState(new Date().getFullYear());
  const [month, setMonth] = useState(new Date().getMonth() + 1);
  
  // 통계 데이터
  const [statisticsData, setStatisticsData] = useState(null);
  const [userStatistics, setUserStatistics] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [dateRangeInvalid, setDateRangeInvalid] = useState(false);
  
  // 콤보박스 데이터
  const [userList, setUserList] = useState([]);
  const [departmentList, setDepartmentList] = useState([]);
  const [ipList, setIpList] = useState([]);
  const [logTypeList, setLogTypeList] = useState([]);
  
  // 뷰 타입: 'chart' 또는 'table'
  const [viewType, setViewType] = useState('chart');
  
  // 표 정렬
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });

  // 사용자별 통계 정렬
  const [userSortConfig, setUserSortConfig] = useState({ key: null, direction: 'asc' });

  // 초기 날짜 설정
  useEffect(() => {
    const today = new Date();
    const sevenDaysAgo = new Date(today);
    sevenDaysAgo.setDate(today.getDate() - 7);
    
    setStartDate(format(sevenDaysAgo, 'yyyy-MM-dd'));
    setEndDate(format(today, 'yyyy-MM-dd'));
  }, []);

  useEffect(() => {
    let cancelled = false;

    const loadComboBoxData = async () => {
      try {
        const [usersRes, departmentsRes, ipsRes, logTypesRes] = await Promise.all([
          isSelfScope ? Promise.resolve({ success: true, data: [] }) : statisticsApi.getUserList(),
          isSelfScope ? Promise.resolve({ success: true, data: [] }) : getDepartmentFilterOptions(FILTER_OPTION_SCREEN_IDS.STATISTICS),
          isSelfScope ? Promise.resolve({ success: true, data: [] }) : statisticsApi.getIpList(),
          logTypeApi.getLogTypeList(true),
        ]);

        if (cancelled) return;

        setUserList(usersRes.success ? (usersRes.data || []) : []);
        setDepartmentList(departmentsRes.success ? (departmentsRes.data || []) : []);
        setIpList(ipsRes.success ? (ipsRes.data || []) : []);
        setLogTypeList(logTypesRes.success ? (logTypesRes.data || []) : []);
      } catch (error) {
        if (!cancelled) {
          console.error('콤보박스 데이터 로드 중 오류:', error);
          setUserList([]);
          setDepartmentList([]);
          setIpList([]);
        }
      }
    };

    loadComboBoxData();

    return () => {
      cancelled = true;
    };
  }, [isSelfScope]);

  useEffect(() => {
    if (!isSelfScope) return;
    const nextLockedSelfFilters = getLockedSelfFilters(selfContext);
    setFilters((prev) => ({
      ...prev,
      ...nextLockedSelfFilters,
      ip: '',
    }));
  }, [isSelfScope, selfContext]);

  /** @param {Object} [filtersOverride] - When provided (e.g. after reset), use instead of state filters */
  const handleSearch = async (filtersOverride) => {
    const currentFilters = filtersOverride != null ? filtersOverride : filters;
    setLoading(true);
    setError(null);

    try {
      let response;
      let userStatsResponse;

      if (statisticsType === 'daily') {
        if (!startDate || !endDate) {
          setError('시작일과 종료일을 선택해주세요.');
          setDateRangeInvalid(false);
          setLoading(false);
          return;
        }
        if (startDate > endDate) {
          setError('종료일은 시작일보다 이전일 수 없습니다.');
          setDateRangeInvalid(true);
          setLoading(false);
          return;
        }
        setDateRangeInvalid(false);
        const effectiveFilters = isSelfScope ? { logType: currentFilters.logType } : currentFilters;
        [response, userStatsResponse] = await Promise.all([
          statisticsApi.getDailyStatistics(startDate, endDate, effectiveFilters),
          statisticsApi.getAllUserStatistics(startDate, endDate, effectiveFilters)
        ]);
      } else {
        setDateRangeInvalid(false);
        if (!year || !month) {
          setError('연도와 월을 선택해주세요.');
          setLoading(false);
          return;
        }
        const monthStart = new Date(year, month - 1, 1);
        const monthEnd = new Date(year, month, 0);
        const monthStartDate = format(monthStart, 'yyyy-MM-dd');
        const monthEndDate = format(monthEnd, 'yyyy-MM-dd');
        const effectiveFilters = isSelfScope ? { logType: currentFilters.logType } : currentFilters;
        [response, userStatsResponse] = await Promise.all([
          statisticsApi.getMonthlyStatistics(year, month, effectiveFilters),
          statisticsApi.getAllUserStatistics(monthStartDate, monthEndDate, effectiveFilters)
        ]);
      }
      
      if (response.success) {
        setStatisticsData(response.data);
        setDateRangeInvalid(false);
      } else {
        setError(response.error || '통계 조회 중 오류가 발생했습니다.');
        setDateRangeInvalid(false);
      }
      
      if (userStatsResponse.success) {
        setUserStatistics(userStatsResponse.data || []);
      }
    } catch (err) {
      console.error('통계 조회 중 오류:', err);
      setError('통계 조회 중 오류가 발생했습니다.');
      setDateRangeInvalid(false);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    try {
      const effectiveFilters = isSelfScope ? { logType: filters.logType } : filters;
      let queryParams;
      if (statisticsType === 'daily') {
        queryParams = {
          startDate,
          endDate,
          ...effectiveFilters
        };
      } else {
        queryParams = {
          year,
          month,
          ...effectiveFilters
        };
      }
      
      const blob = await statisticsApi.exportStatistics(statisticsType, queryParams);
      
      // 파일 다운로드
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `activity_statistics_${statisticsType}_${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (error) {
      console.error('Excel 다운로드 중 오류:', error);
      alert('Excel 다운로드 중 오류가 발생했습니다.');
    }
  };

  const handleFiltersReset = () => {
    const cleared = {
      logType: '',
      ...getLockedSelfFilters(isSelfScope ? selfContext : null),
      ip: '',
    };
    setFilters(cleared);
    setError(null);
    setDateRangeInvalid(false);
    handleSearch(cleared);
  };

  const handleSort = (key) => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const handleUserSort = (key) => {
    let direction = 'asc';
    if (userSortConfig.key === key && userSortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setUserSortConfig({ key, direction });
  };

  return (
    <div className="activity-statistics">
      <h2>활동 로그 통계</h2>
      
      <StatisticsHeader
        statisticsType={statisticsType}
        onTypeChange={setStatisticsType}
      />
      
      <StatisticsFilters
        filters={filters}
        onFiltersChange={setFilters}
        onSearch={() => handleSearch()}
        onReset={handleFiltersReset}
        loading={loading}
        userList={userList}
        departmentList={departmentList}
        ipList={ipList}
        logTypeList={logTypeList}
        isSelfScope={isSelfScope}
        selfContext={lockedSelfFilters}
        statisticsType={statisticsType}
        startDate={startDate}
        endDate={endDate}
        onStartDateChange={(v) => {
          setStartDate(v);
          setError(null);
          setDateRangeInvalid(false);
        }}
        onEndDateChange={(v) => {
          setEndDate(v);
          setError(null);
          setDateRangeInvalid(false);
        }}
        year={year}
        month={month}
        onYearChange={setYear}
        onMonthChange={setMonth}
        dateRangeInvalid={dateRangeInvalid}
        dateRangeErrorId="activity-statistics-date-range-error"
      />
      
      {/* Error id when dateRangeInvalid: must match StatisticsHeader dateRangeErrorId for aria-describedby (date-search.md) */}
      {error && (
        <div
          className="error-message"
          id={dateRangeInvalid ? 'activity-statistics-date-range-error' : undefined}
          role="alert"
        >
          {error}
        </div>
      )}
      
      {/* 그래프/표 토글 + Excel 다운로드만 (검색/초기화는 StatisticsFilters 내부) */}
      <div className="statistics-action-controls">
        <div className="view-toggle">
          <button
            className={viewType === 'chart' ? 'active' : ''}
            onClick={() => setViewType('chart')}
          >
            그래프
          </button>
          <button
            className={viewType === 'table' ? 'active' : ''}
            onClick={() => setViewType('table')}
          >
            표
          </button>
        </div>
        <div className="action-buttons">
          {statisticsData && (
            <button className="export-button" onClick={handleExport}>
              Excel 다운로드
            </button>
          )}
        </div>
      </div>
      
      {statisticsData && (
        <StatisticsView
          statisticsData={statisticsData}
          statisticsType={statisticsType}
          viewType={viewType}
          onViewTypeChange={setViewType}
          sortConfig={sortConfig}
          onSort={handleSort}
        />
      )}
      
      {userStatistics.length > 0 && (
        <UserStatisticsTable
          userStatistics={userStatistics}
          sortConfig={userSortConfig}
          onSort={handleUserSort}
        />
      )}
    </div>
  );
};

export default ActivityStatistics;
