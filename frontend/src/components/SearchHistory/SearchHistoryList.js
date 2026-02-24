import React, { useState, useEffect } from 'react';
import {
  getSearchHistoryList,
  reRequestSearchHistory,
  getSearchHistoryDetail,
} from '../../services/searchHistoryService';
import './SearchHistory.css';
import logger from '../../utils/logger';

const PENDING_SEARCH_KEY = 'pendingSearchFromHistory';

const STATUS_LABEL = {
  PENDING: '대기',
  APPROVED: '승인',
  EXPIRED: '만료',
  REJECTED: '반려',
};

const SearchHistoryList = ({ onBackToMain, onReSearch }) => {
  const [list, setList] = useState([]);
  const [pagination, setPagination] = useState({ currentPage: 1, totalPages: 1, totalCount: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(1);
  const [reRequestingId, setReRequestingId] = useState(null);

  const loadList = async (pageNum = 1) => {
    setLoading(true);
    setError(null);
    try {
      const result = await getSearchHistoryList(pageNum, 20, 'requested_at', 'desc');
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
  }, [page]);

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
      const { logType, searchParams } = result.data;
      if (onReSearch && typeof onReSearch === 'function') {
        onReSearch({ logType, searchParams });
        return;
      }
      sessionStorage.setItem(PENDING_SEARCH_KEY, JSON.stringify({ logType, searchParams }));
      if (onBackToMain) onBackToMain();
    } catch (e) {
      logger.error('재조회 실패:', e);
      setError(e.message || '재조회에 실패했습니다.');
    }
  };

  return (
    <div className="search-history-list">
      <h2>검색 이력 (복호화 승인)</h2>
      {error && <div className="search-history-error">{error}</div>}
      {loading ? (
        <p>목록을 불러오는 중...</p>
      ) : list.length === 0 ? (
        <p>검색 이력이 없습니다. 복호화 승인 요청을 한 검색이 여기에 표시됩니다.</p>
      ) : (
        <>
          <table className="search-history-table">
            <thead>
              <tr>
                <th>순번</th>
                <th>일시</th>
                <th>검색 조건</th>
                <th>복호화 승인 여부</th>
                <th>만료일시</th>
                <th>동작</th>
              </tr>
            </thead>
            <tbody>
              {list.map((row) => (
                <tr key={row.id}>
                  <td>{row.seq}</td>
                  <td>{row.requestedAt}</td>
                  <td className="search-history-summary">{row.searchParamsSummary || '-'}</td>
                  <td>{STATUS_LABEL[row.approvalStatus] || row.approvalStatus}</td>
                  <td>{row.expiresAt}</td>
                  <td>
                    <button
                      type="button"
                      className="search-history-btn re-search"
                      onClick={() => handleReSearch(row)}
                    >
                      재조회
                    </button>
                    {row.isExpired && (
                      <button
                        type="button"
                        className="search-history-btn re-request"
                        onClick={() => handleReRequest(row.id)}
                        disabled={reRequestingId === row.id}
                      >
                        {reRequestingId === row.id ? '처리 중...' : '재요청'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="search-history-pagination">
            <span>총 {pagination.totalCount}건</span>
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              이전
            </button>
            <span>{page} / {pagination.totalPages || 1}</span>
            <button
              type="button"
              disabled={page >= (pagination.totalPages || 1)}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </button>
          </div>
        </>
      )}
    </div>
  );
};

export default SearchHistoryList;
export { PENDING_SEARCH_KEY };
