import React, { useState, useEffect } from 'react';
import UserActivityLogSearchForm from './UserActivityLogSearchForm';
import UserActivityLogTable from './UserActivityLogTable';
import UserActivityLogDetail from './UserActivityLogDetail';
import { searchActivityLogs } from '../../services/userActivityLogService';
import './UserActivityLog.css';
import logger from '../../utils/logger';

const UserActivityLogList = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [selectedLog, setSelectedLog] = useState(null);
  const [searchParams, setSearchParams] = useState({});

  // 초기 로드 - 당일 날짜로 기본 검색
  useEffect(() => {
    const today = new Date();
    const startDate = new Date(today);
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(today);
    endDate.setHours(23, 59, 59, 999);
    
    const formatDate = (date) => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    };
    
    handleSearch({
      startDate: formatDate(startDate),
      endDate: formatDate(endDate),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 검색 실행
  const handleSearch = async (params) => {
    setLoading(true);
    setSearchParams(params);
    setCurrentPage(1);

    try {
      const requestParams = {
        ...params,
        page: 1,
        pageSize: 20,
      };

      logger.debug('🔍 활동 이력 검색 요청:', requestParams);

      const result = await searchActivityLogs(requestParams);

      if (result.success && result.data) {
        setLogs(result.data.data || []);
        setTotalPages(result.data.pagination?.totalPages || 1);
        setTotalCount(result.data.pagination?.totalCount || 0);
        logger.info('✅ 활동 이력 검색 완료:', {
          count: result.data.data?.length || 0,
          total: result.data.pagination?.totalCount || 0,
        });
      } else {
        logger.error('❌ 활동 이력 검색 실패:', result);
        setLogs([]);
        setTotalPages(1);
        setTotalCount(0);
      }
    } catch (error) {
      logger.error('❌ 활동 이력 검색 중 오류:', { error: error.message });
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
        ...searchParams,
        page: page,
        pageSize: 20,
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

      <UserActivityLogSearchForm onSearch={handleSearch} loading={loading} />

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
        />

        {totalPages > 1 && (
          <div className="pagination">
            <button
              className="pagination-button"
              onClick={() => handlePageChange(currentPage - 1)}
              disabled={currentPage === 1 || loading}
            >
              이전
            </button>
            <span className="pagination-info">
              {currentPage} / {totalPages}
            </span>
            <button
              className="pagination-button"
              onClick={() => handlePageChange(currentPage + 1)}
              disabled={currentPage === totalPages || loading}
            >
              다음
            </button>
          </div>
        )}
      </div>

      {selectedLog && (
        <UserActivityLogDetail log={selectedLog} onClose={handleCloseDetail} />
      )}
    </div>
  );
};

export default UserActivityLogList;

