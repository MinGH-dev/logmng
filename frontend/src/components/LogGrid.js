import React, { useState, useEffect } from 'react';
import SearchForm from './SearchForm';
import ImageLogSearchForm from './ImageLogSearchForm';
import AdvancedSearchForm from './AdvancedSearchForm';
import LogTable from './LogTable';
import ImageLogTable from './ImageLogTable';
import { createSearchHistory } from '../services/searchHistoryService';
import './LogGrid.css';
import logger from '../utils/logger';

/** Map logType.id to screen ID for decrypt/allowed API. req 20260318. */
const logTypeIdToScreenId = (logTypeId) => {
  if (logTypeId === 'pb_feplog') return 'pb-feplog';
  if (logTypeId === 'java_fw_imglog') return 'java-fw-imagelog';
  return null;
};

const LogGrid = ({ logType, initialSearchParams, initialSearchApprovalId, onInitialSearchDone, hasDecryptPermission = false }) => {
  const screenId = logType ? logTypeIdToScreenId(logType.id) : null;
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sortConfig, setSortConfig] = useState({ key: 'log_timestamp', direction: 'desc' });

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
  const [saveHistorySuccess, setSaveHistorySuccess] = useState(null);
  /** 복호화 승인 요청 모달: 열림 여부 및 모달 내 요청 사유 (필수, 최대 500자) */
  const [requestReasonModalOpen, setRequestReasonModalOpen] = useState(false);
  const [requestReasonInModal, setRequestReasonInModal] = useState('');
  /** 이번 검색에 대한 복호화 승인 이력 ID. 감사/재조회용; 복호화 허용 여부는 decryption-allowed store 기준 */
  const [currentApprovalId, setCurrentApprovalId] = useState(null);
  /** GET /api/decrypt/allowed 결과 (req 20260318): validUntil, guids — 복호화 버튼 enabled/dimmed 판단용 */
  const [decryptionAllowed, setDecryptionAllowed] = useState({ validUntil: null, guids: [] });

  // 로그 타입에 따라 기본 정렬 필드 설정 (초기화 시 한 번만)
  useEffect(() => {
    if (!logType) return;
    if (logType.id === 'java_fw_imglog' && sortConfig.key === 'log_timestamp') {
      setSortConfig({ key: 'insert_time', direction: sortConfig.direction });
    } else if (logType.id === 'pb_feplog' && sortConfig.key === 'insert_time') {
      setSortConfig({ key: 'log_timestamp', direction: sortConfig.direction });
    }
    // 의도: logType 변경 시에만 정렬 키 동기화. sortConfig 의존 시 불필요 재실행·루프 가능
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [logType?.id]);

  // 검색 이력에서 재조회 시 저장된 조건으로 한 번 검색 실행 + 해당 이력의 승인 ID 유지
  useEffect(() => {
    if (!logType || !initialSearchParams || typeof initialSearchParams !== 'object') return;
    if (initialSearchApprovalId != null) setCurrentApprovalId(initialSearchApprovalId);
    const isAdvanced = initialSearchParams.filters != null || initialSearchParams.queryText != null;
    const preserveApprovalId = initialSearchApprovalId != null;
    if (isAdvanced) {
      handleAdvancedSearch(initialSearchParams, preserveApprovalId);
    } else {
      const params = { ...initialSearchParams, logType: logType.id };
      handleSearch(params, preserveApprovalId);
    }
    if (typeof onInitialSearchDone === 'function') onInitialSearchDone();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [logType?.id, initialSearchParams]);

  // GET /api/decrypt/allowed (req 20260318): 복호화 버튼 enabled/dimmed 판단용. screen=pb-feplog|java-fw-imagelog.
  const fetchDecryptionAllowed = React.useCallback(() => {
    if (!hasDecryptPermission || !screenId || logType?.id !== 'java_fw_imglog') return;
    const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
    fetch(`${apiBaseUrl}/decrypt/allowed?screen=${screenId}`, { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`))))
      .then((json) => {
        const data = json?.data ?? json;
        const validUntil = data?.validUntil ?? data?.valid_until ?? null;
        const guids = Array.isArray(data?.guids) ? data.guids : [];
        setDecryptionAllowed({ validUntil, guids });
      })
      .catch((err) => {
        logger.debug('GET decrypt/allowed failed', { error: err.message });
        setDecryptionAllowed({ validUntil: null, guids: [] });
      });
  }, [hasDecryptPermission, logType?.id, screenId]);

  useEffect(() => {
    fetchDecryptionAllowed();
  }, [fetchDecryptionAllowed]);

  // 검색 결과 변경 시 허용 목록 갱신 (다른 탭에서 승인되었을 수 있음)
  useEffect(() => {
    if (logs.length > 0 && hasDecryptPermission && logType?.id === 'java_fw_imglog') {
      fetchDecryptionAllowed();
    }
  }, [logs.length, hasDecryptPermission, logType?.id, fetchDecryptionAllowed]);
  
  // API 검색 파라미터를 ImageLogSearchForm 폼 초기값 형태로 변환 (재조회 시 폼에 동일 조건 표시)
  const apiParamsToFormValues = (params) => {
    if (!params || typeof params !== 'object') return null;
    const toDatetimeLocal = (s) => {
      if (!s) return '';
      const str = String(s).trim();
      return str.includes(' ') ? str.replace(' ', 'T').substring(0, 19) : str.substring(0, 19);
    };
    const kw = params.keywords;
    const keywordsStr = Array.isArray(kw) ? kw.join(', ') : (kw != null ? String(kw) : '');
    return {
      startDate: toDatetimeLocal(params.startDate),
      endDate: toDatetimeLocal(params.endDate),
      application: params.application != null ? String(params.application) : '',
      servicegroup: params.servicegroup != null ? String(params.servicegroup) : '',
      service: params.service != null ? String(params.service) : '',
      datastring: params.datastring != null ? String(params.datastring) : '',
      headerstring: params.headerstring != null ? String(params.headerstring) : '',
      keywords: keywordsStr,
      showDecryptOption: Boolean(keywordsStr && keywordsStr.trim())
    };
  };

  // 재조회 시: initialSearchParams가 있고 basic이면 폼 초기값을 그걸로 설정(첫 렌더부터 동기화). 아니면 기존처럼 searchParams 기반.
  const isImageLog = logType?.id === 'java_fw_imglog';
  const hasBasicParams = searchParams && (searchParams.startDate || searchParams.endDate);
  const isBasicFromInitial = initialSearchParams && typeof initialSearchParams === 'object' &&
    initialSearchParams.filters == null && initialSearchParams.queryText == null;
  const initialFormValues = isImageLog && (
    isBasicFromInitial
      ? apiParamsToFormValues(initialSearchParams)
      : (searchMode === 'basic' && hasBasicParams ? apiParamsToFormValues(searchParams) : null)
  );

  // logType이 없으면 null 반환
  if (!logType) {
    return null;
  }

  // 검색 실행 함수 (preserveApprovalId: true면 검색 이력 재조회 시 승인 ID 유지)
  const handleSearch = async (params, preserveApprovalId = false) => {
    logger.debug('🔍 프론트엔드에서 받은 파라미터:', { 
      hasParams: !!params,
      logType: logType?.id,
      paramKeys: params ? Object.keys(params) : []
    });
    if (!preserveApprovalId) setCurrentApprovalId(null);
    setLoading(true);
    setSearchParams(params);
    
    try {
      // requestData spreads params first; added keys (logType, page, pageSize, sortField, sortDirection, displayTemplate) do not overwrite params.datastring, params.headerstring, or params.keywords (req 20260318).
      const requestData = {
        ...params,
        logType: logType.id, // 로그 타입 추가
        page: currentPage,
        pageSize,
        sortField: sortConfig.key,
        sortDirection: sortConfig.direction,
        displayTemplate: 'detailed'
      };
      logger.debug('📤 API로 전송할 데이터:', { 
        logType: requestData.logType,
        page: requestData.page,
        pageSize: requestData.pageSize,
        sortField: requestData.sortField,
        sortDirection: requestData.sortDirection
      });
      // Diagnostic (DEBUG only; req 20260318): confirm image log params in request body — length/presence only, no values
      if (logType?.id === 'java_fw_imglog') {
        logger.debug('[LogGrid] image log requestData diagnostic (length/presence only)', {
          imageLogRequestDiagnostic: {
            hasDatastring: 'datastring' in requestData,
            datastringLength: requestData.datastring?.length ?? 0,
            hasHeaderstring: 'headerstring' in requestData,
            headerstringLength: requestData.headerstring?.length ?? 0,
            hasKeywords: 'keywords' in requestData,
            keywordsIsArray: Array.isArray(requestData.keywords),
            keywordsLength: requestData.keywords?.length ?? 0
          }
        });
        // Temporary diagnostic (req 20260318): payload keys and types for image log — remove or guard by env after root cause found. Do not log full values (sensitive).
        if (process.env.NODE_ENV === 'development') {
          console.log('[LogGrid] image log request body diagnostic (keys + types only)', {
            requestBodyKeys: Object.keys(requestData),
            datastring: { present: 'datastring' in requestData, type: typeof requestData.datastring, length: requestData.datastring?.length ?? 0 },
            headerstring: { present: 'headerstring' in requestData, type: typeof requestData.headerstring, length: requestData.headerstring?.length ?? 0 },
            keywords: { present: 'keywords' in requestData, type: typeof requestData.keywords, isArray: Array.isArray(requestData.keywords), length: requestData.keywords?.length ?? 0 }
          });
        }
      }

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
        setTotalCount(result.data?.pagination?.totalCount || result.pagination?.totalCount || 0);
      } else {
        logger.error('❌ API 오류:', { error: result.error });
      }
    } catch (error) {
      logger.error('검색 중 오류 발생:', { error: error.message });
    } finally {
      setLoading(false);
    }
  };

  // 정렬 처리 (단일 sortConfig + onSort 계약)
  const handleSort = async (key) => {
    const newDirection = sortConfig.key === key && sortConfig.direction === 'asc' ? 'desc' : 'asc';
    const nextConfig = { key, direction: newDirection };
    setSortConfig(nextConfig);

    if (Object.keys(searchParams).length > 0) {
      try {
        const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
        const response = await fetch(`${apiBaseUrl}/logs/db-refactored/search`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            ...searchParams,
            logType: logType.id,
            page: currentPage,
            pageSize,
            sortField: nextConfig.key,
            sortDirection: nextConfig.direction,
            displayTemplate: 'detailed',
          }),
        });
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        const result = await response.json();
        if (result.success) {
          const logData = result.data?.data || result.data || [];
          setLogs(logData);
          setTotalPages(result.data?.pagination?.totalPages || result.pagination?.totalPages || 1);
          setTotalCount(result.data?.pagination?.totalCount || result.pagination?.totalCount || 0);
        } else {
          logger.error('API 오류:', { error: result.error });
        }
      } catch (error) {
        logger.error('정렬 중 오류 발생:', { error: error.message });
      }
    }
  };

  // 페이지당 행 수 변경 (즉시 반영, 1페이지로 이동 후 재조회)
  const handlePageSizeChange = (newSize) => {
    setPageSize(newSize);
    setCurrentPage(1);
    if (Object.keys(searchParams).length > 0) {
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      setLoading(true);
      fetch(`${apiBaseUrl}/logs/db-refactored/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          ...searchParams,
          logType: logType.id,
          page: 1,
          pageSize: newSize,
          sortField: sortConfig.key,
          sortDirection: sortConfig.direction,
          displayTemplate: 'detailed',
        }),
      })
        .then((res) => res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`)))
        .then((result) => {
          if (result.success) {
            const logData = result.data?.data || result.data || [];
            setLogs(logData);
            setTotalPages(result.data?.pagination?.totalPages || result.pagination?.totalPages || 1);
            setCurrentPage(1);
            setTotalCount(result.data?.pagination?.totalCount || result.pagination?.totalCount || 0);
          }
        })
        .catch((err) => logger.error('페이지 크기 변경 중 오류:', { error: err.message }))
        .finally(() => setLoading(false));
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
          pageSize,
          sortField: sortConfig.key,
          sortDirection: sortConfig.direction,
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
          setTotalCount(result.data?.pagination?.totalCount || result.pagination?.totalCount || 0);
        } else {
          logger.error('API 오류:', { error: result.error });
        }
      } catch (error) {
        logger.error('페이지 변경 중 오류 발생:', { error: error.message });
      }
    }
  };

  // 복호화 승인 요청 (현재 검색을 이력에 저장). reason 필수.
  // req 20260318: createSearchHistory accepts optional searchResultTotalCount/decryptionTargetCount;
  // API requires both or neither. Main search does not return decryptionTargetCount, so we omit both and let server compute.
  const handleRequestDecryptionApproval = async (reason) => {
    const trimmed = reason != null ? String(reason).trim() : '';
    if (!trimmed) {
      setSaveHistoryError('요청 사유를 입력해 주세요.');
      return;
    }
    setSaveHistoryError(null);
    setSaveHistorySuccess(null);
    setSaveHistoryPending(true);
    try {
      const toSave = searchMode === 'advanced' && lastAdvancedRequest
        ? lastAdvancedRequest
        : { ...searchParams, logType: logType.id };
      const result = await createSearchHistory(logType.id, toSave, trimmed);
      const id = result?.data?.id ?? result?.id;
      if (id != null) setCurrentApprovalId(id);
      logger.info('검색 이력에 복호화 승인 요청 저장됨', { id });
      setSaveHistorySuccess('저장되었습니다. (테스트: 즉시 승인 처리)');
      setTimeout(() => setSaveHistorySuccess(null), 4000);
      setRequestReasonModalOpen(false);
      setRequestReasonInModal('');
      fetchDecryptionAllowed();
    } catch (e) {
      logger.error('복호화 승인 요청 저장 실패:', e);
      setSaveHistoryError(e.message || '저장에 실패했습니다.');
    } finally {
      setSaveHistoryPending(false);
    }
  };

  const openRequestReasonModal = () => {
    setSaveHistoryError(null);
    setRequestReasonInModal('');
    setRequestReasonModalOpen(true);
  };

  const closeRequestReasonModal = () => {
    setRequestReasonModalOpen(false);
    setRequestReasonInModal('');
  };

  // 고급 검색 처리
  const handleAdvancedSearch = async (searchRequest, preserveApprovalId = false) => {
    if (!preserveApprovalId) setCurrentApprovalId(null);
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
        setTotalCount(result.data?.pagination?.totalCount || result.pagination?.totalCount || 0);
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
          <ImageLogSearchForm onSearch={handleSearch} initialFormValues={initialFormValues} />
        )
      ) : (
        <SearchForm onSearch={handleSearch} />
      )}
      <div className="log-grid-actions">
        {!hasDecryptPermission && (
          <span className="decrypt-permission-message" role="status">복호화 권한이 없습니다.</span>
        )}
        {hasDecryptPermission && (
          <>
            <button
              type="button"
              className="decrypt-approval-request-btn"
              onClick={openRequestReasonModal}
              disabled={saveHistoryPending || (Object.keys(searchParams).length === 0 && !lastAdvancedRequest)}
              aria-label="복호화 승인 요청"
            >
              복호화 승인 요청
            </button>
            {saveHistorySuccess && <span className="decrypt-approval-success">{saveHistorySuccess}</span>}
            {saveHistoryError && <span className="decrypt-approval-error">{saveHistoryError}</span>}
          </>
        )}
      </div>
      {requestReasonModalOpen && (
        <div className="log-grid-request-reason-modal-overlay" role="dialog" aria-labelledby="log-grid-request-reason-modal-title" aria-modal="true">
          <div className="log-grid-request-reason-modal">
            <h3 id="log-grid-request-reason-modal-title">요청 사유 (필수)</h3>
            <input
              id="log-grid-request-reason-input"
              type="text"
              className="log-grid-request-reason-input form-control"
              value={requestReasonInModal}
              onChange={(e) => setRequestReasonInModal(e.target.value)}
              placeholder="복호화 승인 요청 사유를 입력하세요"
              maxLength={500}
              aria-required="true"
              aria-invalid={!!(saveHistoryError && !requestReasonInModal?.trim())}
              aria-describedby="log-grid-request-reason-error"
            />
            {saveHistoryError && requestReasonModalOpen && (
              <p id="log-grid-request-reason-error" className="log-grid-request-reason-modal-error" role="alert">
                {saveHistoryError}
              </p>
            )}
            <div className="log-grid-request-reason-modal-actions">
              <button type="button" className="log-grid-request-reason-modal-cancel" onClick={closeRequestReasonModal}>
                취소
              </button>
              <button
                type="button"
                className="decrypt-approval-request-btn"
                onClick={() => handleRequestDecryptionApproval(requestReasonInModal)}
                disabled={saveHistoryPending || !(requestReasonInModal != null && String(requestReasonInModal).trim())}
                aria-label="요청 사유 제출"
              >
                {saveHistoryPending ? '저장 중...' : '제출'}
              </button>
            </div>
          </div>
        </div>
      )}
      {isImageLog ? (
        <ImageLogTable
          logs={logs}
          loading={loading}
          sortConfig={sortConfig}
          onSort={handleSort}
          currentPage={currentPage}
          totalPages={totalPages}
          totalCount={totalCount}
          onPageChange={handlePageChange}
          pageSize={pageSize}
          onPageSizeChange={handlePageSizeChange}
          keywords={searchParams.keywords || []}
          searchParams={searchParams}
          searchHistoryId={currentApprovalId}
          hasDecryptPermission={hasDecryptPermission}
          decryptionAllowed={decryptionAllowed}
          screenId={screenId}
        />
      ) : (
        <LogTable
          logs={logs}
          loading={loading}
          sortConfig={sortConfig}
          onSort={handleSort}
          currentPage={currentPage}
          totalPages={totalPages}
          totalCount={totalCount}
          onPageChange={handlePageChange}
          pageSize={pageSize}
          onPageSizeChange={handlePageSizeChange}
          keywords={searchParams.keywords || []}
        />
      )}
    </div>
  );
};

export default LogGrid; 