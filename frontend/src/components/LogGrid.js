import React, { useState, useEffect } from 'react';
import SearchForm from './SearchForm';
import ImageLogSearchForm from './ImageLogSearchForm';
import AdvancedSearchForm from './AdvancedSearchForm';
import LogTable from './LogTable';
import ImageLogTable from './ImageLogTable';
import { createSearchHistory } from '../services/searchHistoryService';
import './LogGrid.css';
import logger from '../utils/logger';

const LogGrid = ({ logType, initialSearchParams, onInitialSearchDone }) => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [sortField, setSortField] = useState('log_timestamp');
  const [sortDirection, setSortDirection] = useState('desc');

  // 검색 조건 상태
  const [searchParams, setSearchParams] = useState({
    startDate: '',
    endDate: '',
    mediaCode: '',
    trCode: '',
    loginId: '',
    accountNumbers: []
  });
  
  // 검색 모드 상태 (기본: 필드별 검색)
  const [searchMode, setSearchMode] = useState('basic'); // 'basic' | 'advanced'
  const [lastAdvancedRequest, setLastAdvancedRequest] = useState(null);
  const [saveHistoryPending, setSaveHistoryPending] = useState(false);
  const [saveHistoryError, setSaveHistoryError] = useState(null);
  
  // 로그 타입에 따라 기본 정렬 필드 설정 (초기화 시 한 번만)
  useEffect(() => {
    if (!logType) return;
    
    if (logType.id === 'java_fw_imglog' && sortField === 'log_timestamp') {
      setSortField('insert_time');
    } else if (logType.id === 'pb_feplog' && sortField === 'insert_time') {
      setSortField('log_timestamp');
    }
  }, [logType?.id]); // logType.id가 변경될 때만 실행

  // 검색 이력에서 재조회 시 저장된 조건으로 한 번 검색 실행
  useEffect(() => {
    if (!logType || !initialSearchParams || typeof initialSearchParams !== 'object') return;
    const isAdvanced = initialSearchParams.filters != null || initialSearchParams.queryText != null;
    if (isAdvanced) {
      handleAdvancedSearch(initialSearchParams);
    } else {
      const params = { ...initialSearchParams, logType: logType.id };
      handleSearch(params);
    }
    if (typeof onInitialSearchDone === 'function') onInitialSearchDone();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [logType?.id, initialSearchParams]);
  
  // logType이 없으면 null 반환
  if (!logType) {
    return null;
  }

  // 검색 실행 함수
  const handleSearch = async (params) => {
    logger.debug('🔍 프론트엔드에서 받은 파라미터:', { 
      hasParams: !!params,
      logType: logType?.id,
      paramKeys: params ? Object.keys(params) : []
    });
    setLoading(true);
    setSearchParams(params);
    
    try {
      const requestData = {
        ...params,
        logType: logType.id, // 로그 타입 추가
        page: currentPage,
        pageSize: 10,
        sortField: sortField,
        sortDirection: sortDirection,
        displayTemplate: 'detailed'
      };
      logger.debug('📤 API로 전송할 데이터:', { 
        logType: requestData.logType,
        page: requestData.page,
        pageSize: requestData.pageSize,
        sortField: requestData.sortField,
        sortDirection: requestData.sortDirection
      });
      
      // 실제 API 호출
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      const response = await fetch(`${apiBaseUrl}/logs/db-refactored/search`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // 세션 쿠키 전달
        body: JSON.stringify(requestData)
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result = await response.json();
      logger.debug('📥 API 응답:', {
        success: result.success,
        dataType: typeof result.data,
        dataKeys: result.data ? Object.keys(result.data) : [],
        dataDataLength: result.data?.data?.length,
        hasPagination: !!result.data?.pagination,
        totalCount: result.data?.pagination?.totalCount || result.data?.totalCount,
        totalPages: result.data?.pagination?.totalPages || result.data?.totalPages
      });
      
      if (result.success) {
        const logData = result.data?.data || result.data || [];
        logger.info('✅ 검색 성공:', { count: logData.length });
        logger.debug('📊 검색 결과 상세:', {
          logDataLength: logData.length,
          hasFirstLog: !!logData[0],
          pagination: result.data?.pagination
        });
        setLogs(logData);
        setTotalPages(result.data?.pagination?.totalPages || result.pagination?.totalPages || 1);
        setCurrentPage(result.data?.pagination?.currentPage || result.pagination?.currentPage || 1);
      } else {
        logger.error('❌ API 오류:', { error: result.error });
      }
    } catch (error) {
      logger.error('검색 중 오류 발생:', { error: error.message });
    } finally {
      setLoading(false);
    }
  };

  // 정렬 처리
  const handleSort = async (field) => {
    let newSortDirection = 'asc';
    
    if (sortField === field) {
      newSortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
    }
    
    setSortField(field);
    setSortDirection(newSortDirection);
    
    // 현재 검색 조건으로 API 재호출
    if (Object.keys(searchParams).length > 0) {
      try {
        const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
        const response = await fetch(`${apiBaseUrl}/logs/db-refactored/search`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          credentials: 'include', // 세션 쿠키 전달
        body: JSON.stringify({
          ...searchParams,
          logType: logType.id, // 로그 타입 추가
          page: currentPage,
          pageSize: 10,
          sortField: field,
          sortDirection: newSortDirection,
          displayTemplate: 'detailed'
        })
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.json();
        
        if (result.success) {
          const logData = result.data?.data || result.data || [];
          setLogs(logData);
          setTotalPages(result.data?.pagination?.totalPages || result.pagination?.totalPages || 1);
        } else {
          logger.error('API 오류:', { error: result.error });
        }
      } catch (error) {
        logger.error('정렬 중 오류 발생:', { error: error.message });
      }
    }
  };

  // 페이지 변경
  const handlePageChange = async (page) => {
    setCurrentPage(page);
    
    // 현재 검색 조건으로 API 재호출
    if (Object.keys(searchParams).length > 0) {
      try {
        const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
        const response = await fetch(`${apiBaseUrl}/logs/db-refactored/search`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          credentials: 'include', // 세션 쿠키 전달
        body: JSON.stringify({
          ...searchParams,
          logType: logType.id, // 로그 타입 추가
          page: page,
          pageSize: 10,
          sortField: sortField,
          sortDirection: sortDirection,
          displayTemplate: 'detailed'
        })
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.json();
        
        if (result.success) {
          const logData = result.data?.data || result.data || [];
          setLogs(logData);
          setTotalPages(result.data?.pagination?.totalPages || result.pagination?.totalPages || 1);
        } else {
          logger.error('API 오류:', { error: result.error });
        }
      } catch (error) {
        logger.error('페이지 변경 중 오류 발생:', { error: error.message });
      }
    }
  };



  const isImageLog = logType && logType.id === 'java_fw_imglog';
  
  // 복호화 승인 요청 (현재 검색을 이력에 저장)
  const handleRequestDecryptionApproval = async () => {
    setSaveHistoryError(null);
    setSaveHistoryPending(true);
    try {
      const toSave = searchMode === 'advanced' && lastAdvancedRequest
        ? lastAdvancedRequest
        : { ...searchParams, logType: logType.id };
      await createSearchHistory(logType.id, toSave);
      logger.info('검색 이력에 복호화 승인 요청 저장됨');
    } catch (e) {
      logger.error('복호화 승인 요청 저장 실패:', e);
      setSaveHistoryError(e.message || '저장에 실패했습니다.');
    } finally {
      setSaveHistoryPending(false);
    }
  };

  // 고급 검색 처리
  const handleAdvancedSearch = async (searchRequest) => {
    setLastAdvancedRequest(searchRequest);
    setLoading(true);
    setSearchParams({});
    setCurrentPage(1);
    
    try {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      const response = await fetch(`${apiBaseUrl}/logs/db-refactored/advanced-search`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // 세션 쿠키 전달
        body: JSON.stringify(searchRequest)
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result = await response.json();
      logger.debug('📥 고급 검색 API 응답:', {
        success: result.success,
        hasData: !!result.data,
        dataLength: result.data?.data?.length,
        hasPagination: !!result.data?.pagination
      });
      
      if (result.success) {
        const logData = result.data?.data || result.data || [];
        logger.info('✅ 검색 성공:', { count: logData.length });
        setLogs(logData);
        setTotalPages(result.data?.pagination?.totalPages || result.pagination?.totalPages || 1);
        setCurrentPage(result.data?.pagination?.currentPage || result.pagination?.currentPage || 1);
      } else {
        logger.error('❌ API 오류:', { error: result.error });
      }
    } catch (error) {
      logger.error('검색 중 오류 발생:', { error: error.message });
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <div className="log-grid">
      <div className="log-grid-header">
        <h2>{logType?.name || '로그 검색'}</h2>
        <p className="log-type-description">{logType?.description || ''}</p>
      </div>
      {isImageLog && (
        <div className="search-mode-selector">
          <button
            className={`search-mode-btn ${searchMode === 'basic' ? 'active' : ''}`}
            onClick={() => setSearchMode('basic')}
          >
            필드별 검색
          </button>
          <button
            className={`search-mode-btn ${searchMode === 'advanced' ? 'active' : ''}`}
            onClick={() => setSearchMode('advanced')}
          >
            조건식 검색
          </button>
        </div>
      )}
      {isImageLog ? (
        searchMode === 'advanced' ? (
          <AdvancedSearchForm logType={logType} onSearch={handleAdvancedSearch} />
        ) : (
          <ImageLogSearchForm onSearch={handleSearch} />
        )
      ) : (
        <SearchForm onSearch={handleSearch} />
      )}
      <div className="log-grid-actions">
        <button
          type="button"
          className="decrypt-approval-request-btn"
          onClick={handleRequestDecryptionApproval}
          disabled={saveHistoryPending || (Object.keys(searchParams).length === 0 && !lastAdvancedRequest)}
        >
          {saveHistoryPending ? '저장 중...' : '복호화 승인 요청'}
        </button>
        {saveHistoryError && <span className="decrypt-approval-error">{saveHistoryError}</span>}
      </div>
      {isImageLog ? (
        <ImageLogTable
          logs={logs}
          loading={loading}
          sortField={sortField}
          sortDirection={sortDirection}
          onSort={handleSort}
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
          keywords={searchParams.keywords || []}
          searchParams={searchParams}
        />
      ) : (
        <LogTable 
          logs={logs}
          loading={loading}
          sortField={sortField}
          sortDirection={sortDirection}
          onSort={handleSort}
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
          keywords={searchParams.keywords || []}
        />
      )}
    </div>
  );
};

export default LogGrid; 