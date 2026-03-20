import React, { useState, useEffect } from 'react';
import UserActivityLogSearchForm from './UserActivityLogSearchForm';
import UserActivityLogTable from './UserActivityLogTable';
import UserActivityLogDetail from './UserActivityLogDetail';
import { searchActivityLogs } from '../../services/userActivityLogService';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../../services/filterOptionsService';
import { getSelfContextForDisplay } from '../../utils/security';
import './UserActivityLog.css';
import logger from '../../utils/logger';
import { getApiBaseUrl } from '../../config/runtimeApi';
const SELF_SCOPE_OMIT_FIELDS = ['userId', 'username', 'department', 'ipAddress'];

const sanitizeSearchParamsForScope = (params = {}, isSelfScope = false) => {
  const normalizedParams = { ...params };

  if (!isSelfScope) {
    return normalizedParams;
  }

  SELF_SCOPE_OMIT_FIELDS.forEach((field) => {
    delete normalizedParams[field];
  });

  return normalizedParams;
};

const UserActivityLogList = ({ user }) => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [selectedLog, setSelectedLog] = useState(null);
  const [searchParams, setSearchParams] = useState({});
  const [authError, setAuthError] = useState(null);
  const [serverToday, setServerToday] = useState(null);
  const [departmentList, setDepartmentList] = useState([]);

  const isSelfScope = !user?.isSystemAdmin && user?.screenScopes?.['activity-log'] === 'self';
  const selfContext = getSelfContextForDisplay(user);

  // 부서 목록 로드 (scope≠self일 때 검색 폼 드롭다운용)
  useEffect(() => {
    if (isSelfScope) {
      setDepartmentList([]);
      return;
    }
    getDepartmentFilterOptions(FILTER_OPTION_SCREEN_IDS.ACTIVITY_LOG)
      .then((res) => {
        if (res.success && Array.isArray(res.data)) {
          setDepartmentList(res.data);
        }
      })
      .catch(() => {
        setDepartmentList([]);
      });
  }, [isSelfScope]);

  useEffect(() => {
    if (!isSelfScope) return;
    setSearchParams((prev) => sanitizeSearchParamsForScope(prev, true));
  }, [isSelfScope]);

  // 초기 로드 - 서버 날짜(health) 기준 '오늘'로 검색 (브라우저/서버 타임존 불일치 방지)
  useEffect(() => {
    let cancelled = false;
    fetch(`${getApiBaseUrl()}/health`, { credentials: 'include' })
      .then((res) => res.json())
      .then((res) => {
        if (cancelled) return;
        const dateStr = res.success && res.data && res.data.timestamp
          ? String(res.data.timestamp).slice(0, 10)
          : null;
        if (dateStr) {
          setServerToday(dateStr);
          handleSearch({
            startDate: `${dateStr} 00:00:00`,
            endDate: `${dateStr} 23:59:59`,
          });
        } else {
          const today = new Date();
          const y = today.getFullYear();
          const m = String(today.getMonth() + 1).padStart(2, '0');
          const d = String(today.getDate()).padStart(2, '0');
          handleSearch({
            startDate: `${y}-${m}-${d} 00:00:00`,
            endDate: `${y}-${m}-${d} 23:59:59`,
          });
        }
      })
      .catch(() => {
        if (!cancelled) {
          const today = new Date();
          const y = today.getFullYear();
          const m = String(today.getMonth() + 1).padStart(2, '0');
          const d = String(today.getDate()).padStart(2, '0');
          handleSearch({
            startDate: `${y}-${m}-${d} 00:00:00`,
            endDate: `${y}-${m}-${d} 23:59:59`,
          });
        }
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 검색 실행
  const handleSearch = async (params) => {
    setLoading(true);
    setCurrentPage(1);

    try {
      const sanitizedParams = sanitizeSearchParamsForScope(params, isSelfScope);
      setSearchParams(sanitizedParams);
      const requestParams = { ...sanitizedParams, page: 1, pageSize };

      logger.debug('🔍 활동 이력 검색 요청:', requestParams);

      const result = await searchActivityLogs(requestParams);

      if (result.success && result.data) {
        setAuthError(null);
        setLogs(result.data.data || []);
        setTotalPages(result.data.pagination?.totalPages || 1);
        setTotalCount(result.data.pagination?.totalCount || 0);
        logger.info('✅ 활동 이력 검색 완료:', {
          count: result.data.data?.length || 0,
          total: result.data.pagination?.totalCount || 0,
        });
      } else if (result.code === 'UNAUTHORIZED' || (result.error && result.error.includes('로그인'))) {
        setAuthError(result.error || '로그인이 필요합니다.');
        setLogs([]);
        setTotalPages(1);
        setTotalCount(0);
      } else {
        logger.error('❌ 활동 이력 검색 실패:', result);
        setAuthError(null);
        setLogs([]);
        setTotalPages(1);
        setTotalCount(0);
      }
    } catch (error) {
      logger.error('❌ 활동 이력 검색 중 오류:', { error: error.message });
      setAuthError(null);
      setLogs([]);
      setTotalPages(1);
      setTotalCount(0);
    } finally {
      setLoading(false);
    }
  };

  // 페이지 변경
  const handlePageChange = async (page) => {
    setLoading(true);
    setCurrentPage(page);

    try {
      const requestParams = {
        ...sanitizeSearchParamsForScope(searchParams, isSelfScope),
        page,
        pageSize,
      };
      const result = await searchActivityLogs(requestParams);

      if (result.success && result.data) {
        setLogs(result.data.data || []);
        setTotalPages(result.data.pagination?.totalPages || 1);
        setTotalCount(result.data.pagination?.totalCount || 0);
      }
    } catch (error) {
      logger.error('❌ 페이지 변경 중 오류:', { error: error.message });
    } finally {
      setLoading(false);
    }
  };

  const handlePageSizeChange = (newSize) => {
    setPageSize(newSize);
    setCurrentPage(1);
    const sanitizedParams = sanitizeSearchParamsForScope(searchParams, isSelfScope);
    setSearchParams(sanitizedParams);
    const requestParams = { ...sanitizedParams, page: 1, pageSize: newSize };
    setLoading(true);
    searchActivityLogs(requestParams)
      .then((result) => {
        if (result.success && result.data) {
          setLogs(result.data.data || []);
          setTotalPages(result.data.pagination?.totalPages || 1);
          setTotalCount(result.data.pagination?.totalCount || 0);
        }
      })
      .catch((error) => logger.error('❌ 페이지 크기 변경 중 오류:', { error: error.message }))
      .finally(() => setLoading(false));
  };

  // 행 클릭 (상세 조회)
  const handleRowClick = async (log) => {
    try {
      const { getActivityLogDetail } = await import('../../services/userActivityLogService');
      const result = await getActivityLogDetail(log.id);

      if (result.success && result.data) {
        setSelectedLog(result.data);
      }
    } catch (error) {
      logger.error('❌ 활동 이력 상세 조회 실패:', { error: error.message });
    }
  };

  // 상세 모달 닫기
  const handleCloseDetail = () => {
    setSelectedLog(null);
  };

  return (
    <div className="activity-log-list-container">
      <div className="activity-log-header">
        <h1>사용자 활동 이력</h1>
        <p className="activity-log-description">
          시스템에서 사용자가 수행한 모든 활동을 조회할 수 있습니다.
        </p>
      </div>

      <UserActivityLogSearchForm
        onSearch={handleSearch}
        loading={loading}
        initialServerDate={serverToday}
        isSelfScope={isSelfScope}
        departmentList={departmentList}
        selfContext={selfContext}
      />

      {authError && (
        <div className="activity-log-auth-error" role="alert">
          {authError} 로그인 후 다시 시도해 주세요.
        </div>
      )}

      <div className="activity-log-results">
        <div className="results-header">
          <span className="results-count">
            총 {totalCount.toLocaleString()}건
          </span>
        </div>

        <UserActivityLogTable
          logs={logs}
          onRowClick={handleRowClick}
          loading={loading}
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
          totalCount={totalCount}
          pageSize={pageSize}
          onPageSizeChange={handlePageSizeChange}
        />
      </div>

      {selectedLog && (
        <UserActivityLogDetail log={selectedLog} onClose={handleCloseDetail} />
      )}
    </div>
  );
};

export default UserActivityLogList;

