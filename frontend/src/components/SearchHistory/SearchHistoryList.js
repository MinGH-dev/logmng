import React, { useState, useEffect } from 'react';
import {
  getSearchHistoryList,
  reRequestSearchHistory,
  getSearchHistoryDetail,
} from '../../services/searchHistoryService';
import DataTable, { EmptyTableBody } from '../DataTable';
import './SearchHistory.css';
import logger from '../../utils/logger';

const SEARCH_HISTORY_COLUMNS = [
  { key: 'seq', label: '순번', sortable: true },
  { key: 'requested_at', label: '일시', sortable: true },
  { key: 'searchParamsSummary', label: '검색 조건', sortable: false },
  { key: 'approvalStatus', label: '복호화 승인 여부', sortable: false },
  { key: 'approvalHistory', label: '결재 이력', sortable: false },
  { key: 'expiresAt', label: '만료일시', sortable: false },
  { key: 'actions', label: '동작', sortable: false },
];

const PENDING_SEARCH_KEY = 'pendingSearchFromHistory';

const STATUS_LABEL = {
  PENDING: '대기',
  APPROVED: '승인',
  EXPIRED: '만료',
  REJECTED: '반려',
};

/** 결재 이력 한 줄 요약: 승인/반려 시 결재자와 일시, 없으면 "-" */
function ApprovalHistoryCell({ row }) {
  const status = row.approvalStatus;
  if (status === 'APPROVED') {
    const by = row.approvedBy;
    const at = row.approvedAt;
    if (!by && !at) return <td>-</td>;
    const text = `승인 ${by || '-'} ${at || ''}`.trim();
    return <td>{text}</td>;
  }
  if (status === 'REJECTED') {
    const by = row.rejectedBy;
    const at = row.rejectedAt;
    const reason = row.rejectionReason;
    if (!by && !at) return <td>{reason ? <span title={reason}>-</span> : '-'}</td>;
    const text = `반려 ${by || '-'} ${at || ''}`.trim();
    const content = reason ? <span title={reason}>{text}</span> : text;
    return <td>{content}</td>;
  }
  return <td>-</td>;
}

const SEARCH_PARAMS_LABELS = {
  startDate: '시작일시',
  endDate: '종료일시',
  application: '시스템 명',
  servicegroup: '서비스그룹',
  service: '서비스명',
  datastring: '데이터',
  headerstring: '헤더',
  keywords: '키워드',
  queryText: '쿼리',
  filters: '필터 목록',
  logType: '로그 타입',
};

function formatDetailValue(value) {
  if (value == null || value === '') return '—';
  if (Array.isArray(value)) return value.length ? value.join(', ') : '—';
  if (typeof value === 'object') return null;
  return String(value);
}

function SearchParamsDetailView({ searchParams }) {
  if (!searchParams || typeof searchParams !== 'object') return <p>검색 조건 없음</p>;
  const entries = Object.entries(searchParams).filter(([key]) => key !== 'logType');
  return (
    <div className="search-history-detail-body">
      {entries.map(([key, value]) => {
        const label = SEARCH_PARAMS_LABELS[key] || key;
        if (Array.isArray(value) && value.length > 0 && value.every((item) => typeof item === 'object' && item !== null)) {
          return (
            <div key={key} className="search-history-detail-row search-history-detail-nested">
              <span className="search-history-detail-key">{label}</span>
              <span className="search-history-detail-value">
                <ul className="search-history-detail-sublist">
                  {value.map((item, idx) => (
                    <li key={idx}>
                      {typeof item === 'object' ? (
                        <pre>{JSON.stringify(item, null, 2)}</pre>
                      ) : (
                        String(item)
                      )}
                    </li>
                  ))}
                </ul>
              </span>
            </div>
          );
        }
        const display = formatDetailValue(value);
        return (
          <div key={key} className="search-history-detail-row">
            <span className="search-history-detail-key">{label}</span>
            <span className="search-history-detail-value">{display}</span>
          </div>
        );
      })}
    </div>
  );
}

const SearchHistoryList = ({ onBackToMain, onReSearch }) => {
  const [list, setList] = useState([]);
  const [pagination, setPagination] = useState({ currentPage: 1, totalPages: 1, totalCount: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sortConfig, setSortConfig] = useState({ key: 'requested_at', direction: 'desc' });
  const [reRequestingId, setReRequestingId] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [detailData, setDetailData] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);

  const loadList = async (pageNum = 1) => {
    setLoading(true);
    setError(null);
    try {
      const result = await getSearchHistoryList(pageNum, pageSize, sortConfig.key, sortConfig.direction);
      if (result.success && result.data) {
        setList(result.data.data || []);
        setPagination(result.data.pagination || { currentPage: 1, totalPages: 1, totalCount: 0 });
      }
    } catch (e) {
      logger.error('검색 이력 목록 조회 실패:', e);
      setError(e.message || '목록을 불러오지 못했습니다.');
      setList([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadList(page);
  }, [page, pageSize, sortConfig.key, sortConfig.direction]);

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
    setPage(1);
  };

  const handlePageSizeChange = (newSize) => {
    setPageSize(newSize);
    setPage(1);
  };

  const handleReRequest = async (id) => {
    setReRequestingId(id);
    try {
      await reRequestSearchHistory(id);
      await loadList(page);
    } catch (e) {
      logger.error('재요청 실패:', e);
      setError(e.message || '재요청에 실패했습니다.');
    } finally {
      setReRequestingId(null);
    }
  };

  const handleReSearch = async (item) => {
    try {
      const result = await getSearchHistoryDetail(item.id);
      if (!result.success || !result.data) throw new Error('상세 조회 실패');
      const { logType, searchParams, id: searchHistoryId } = result.data;
      if (onReSearch && typeof onReSearch === 'function') {
        onReSearch({ logType, searchParams, id: searchHistoryId });
        return;
      }
      sessionStorage.setItem(PENDING_SEARCH_KEY, JSON.stringify({ logType, searchParams }));
      if (onBackToMain) onBackToMain();
    } catch (e) {
      logger.error('재조회 실패:', e);
      setError(e.message || '재조회에 실패했습니다.');
    }
  };

  const handleViewDetail = async (row) => {
    setDetailModalOpen(true);
    setDetailData(null);
    setDetailError(null);
    setDetailLoading(true);
    try {
      const result = await getSearchHistoryDetail(row.id);
      if (!result.success || !result.data) throw new Error('상세 조회 실패');
      setDetailData(result.data);
    } catch (e) {
      logger.error('자세히 보기 조회 실패:', e);
      setDetailError(e.message || '상세를 불러오지 못했습니다.');
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetailModal = () => {
    setDetailModalOpen(false);
    setDetailData(null);
    setDetailError(null);
  };

  const handleDetailOverlayClick = (e) => {
    if (e.target === e.currentTarget) closeDetailModal();
  };

  useEffect(() => {
    if (!detailModalOpen) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') closeDetailModal();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [detailModalOpen]);

  return (
    <div className="search-history-list">
      <h2>검색 이력 (복호화 승인)</h2>
      {error && <div className="search-history-error">{error}</div>}
      <DataTable
        columns={SEARCH_HISTORY_COLUMNS}
        sortConfig={sortConfig}
        onSort={handleSort}
        loading={loading}
        emptyMessage="검색 이력이 없습니다. 복호화 승인 요청을 한 검색이 여기에 표시됩니다."
        emptyColSpan={7}
        ariaLabel="검색 이력 목록"
        pagination={
          (pagination.totalPages || 1) > 1
            ? {
                currentPage: page,
                totalPages: pagination.totalPages || 1,
                onPageChange: (p) => setPage(p),
                simple: true,
                infoText: `총 ${pagination.totalCount}건`,
              }
            : null
        }
        pageSize={pageSize}
        onPageSizeChange={handlePageSizeChange}
      >
        {list.length === 0 ? (
          <EmptyTableBody colSpan={7} message="검색 이력이 없습니다. 복호화 승인 요청을 한 검색이 여기에 표시됩니다." />
        ) : (
          list.map((row) => (
            <tr key={row.id}>
              <td>{row.seq}</td>
              <td>{row.requestedAt}</td>
              <td className="search-history-summary">{row.searchParamsSummary || '-'}</td>
              <td>{STATUS_LABEL[row.approvalStatus] || row.approvalStatus}</td>
              <ApprovalHistoryCell row={row} />
              <td>{row.expiresAt}</td>
              <td>
                <button type="button" className="search-history-btn re-search" onClick={() => handleReSearch(row)} aria-label={`재조회, ID ${row.id}`}>재조회</button>
                <button type="button" className="search-history-btn detail" onClick={() => handleViewDetail(row)} aria-label={`자세히 보기, ID ${row.id}`}>자세히 보기</button>
                {row.isExpired && (
                  <button type="button" className="search-history-btn re-request" onClick={() => handleReRequest(row.id)} disabled={reRequestingId === row.id} aria-label={reRequestingId === row.id ? '재요청 처리 중' : `재요청, ID ${row.id}`}>
                    {reRequestingId === row.id ? '처리 중...' : '재요청'}
                  </button>
                )}
              </td>
            </tr>
          ))
        )}
      </DataTable>

      {detailModalOpen && (
        <div
          className="search-history-detail-modal"
          onClick={handleDetailOverlayClick}
          role="dialog"
          aria-modal="true"
          aria-labelledby="search-history-detail-title"
        >
          <div className="search-history-detail-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="search-history-detail-header">
              <h3 id="search-history-detail-title">검색 조건 상세</h3>
              <button
                type="button"
                className="search-history-detail-close"
                onClick={closeDetailModal}
                aria-label="닫기"
              >
                닫기
              </button>
            </div>
            <div className="search-history-detail-content">
              {detailLoading && <p>불러오는 중...</p>}
              {detailError && <div className="search-history-error">{detailError}</div>}
              {!detailLoading && !detailError && detailData && (
                <>
                  {detailData.logType && (
                    <div className="search-history-detail-row">
                      <span className="search-history-detail-key">로그 타입</span>
                      <span className="search-history-detail-value">
                        {detailData.logType.name || detailData.logType.id || '-'}
                      </span>
                    </div>
                  )}
                  <SearchParamsDetailView searchParams={detailData.searchParams} />
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SearchHistoryList;
export { PENDING_SEARCH_KEY };
