import React, { useState, useEffect } from 'react';
import {
  getPendingList,
  approveSearchHistory,
  rejectSearchHistory,
} from '../../services/searchHistoryService';
import logger from '../../utils/logger';
import './PendingApprovals.css';

const FORBIDDEN_CODES = ['FORBIDDEN_NOT_APPROVER', 'NOT_APPROVER'];

const PendingApprovals = ({ onBackToMain }) => {
  const [list, setList] = useState([]);
  const [pagination, setPagination] = useState({ currentPage: 1, totalPages: 1, totalCount: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [page, setPage] = useState(1);
  const [actionId, setActionId] = useState(null);
  const [rejectModal, setRejectModal] = useState(null); // { id, reason }

  const loadList = async (pageNum = 1) => {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const result = await getPendingList(pageNum, 20);
      const data = result.data;
      setList(data?.data || []);
      setPagination(data?.pagination || { currentPage: 1, totalPages: 1, totalCount: 0 });
    } catch (e) {
      logger.error('승인 대기 목록 조회 실패:', e);
      if (e.status === 403 || (e.code && FORBIDDEN_CODES.includes(e.code))) {
        setError('승인 권한이 없습니다. 지정된 결재자 또는 관리자만 접근할 수 있습니다.');
      } else {
        setError(e.message || '목록을 불러오지 못했습니다.');
      }
      setList([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadList(page);
  }, [page]);

  const handleApprove = async (id) => {
    setActionId(id);
    setError(null);
    setMessage(null);
    try {
      await approveSearchHistory(id);
      setMessage('승인되었습니다.');
      await loadList(page);
    } catch (e) {
      logger.error('승인 실패:', e);
      if (e.status === 403 || (e.code && FORBIDDEN_CODES.includes(e.code))) {
        setError('승인 권한이 없습니다.');
      } else {
        setError(e.message || '승인에 실패했습니다.');
      }
    } finally {
      setActionId(null);
    }
  };

  const openRejectModal = (id) => {
    setRejectModal({ id, reason: '' });
  };

  const closeRejectModal = () => {
    setRejectModal(null);
  };

  const handleRejectSubmit = async () => {
    if (!rejectModal) return;
    const { id, reason } = rejectModal;
    setActionId(id);
    setError(null);
    setMessage(null);
    try {
      await rejectSearchHistory(id, reason || undefined);
      setMessage('반려되었습니다.');
      closeRejectModal();
      await loadList(page);
    } catch (e) {
      logger.error('반려 실패:', e);
      if (e.status === 403 || (e.code && FORBIDDEN_CODES.includes(e.code))) {
        setError('승인 권한이 없습니다.');
      } else {
        setError(e.message || '반려에 실패했습니다.');
      }
    } finally {
      setActionId(null);
    }
  };

  return (
    <div className="pending-approvals">
      <h2>승인 대기</h2>
      {onBackToMain && (
        <button type="button" className="pending-approvals-back back-button" onClick={onBackToMain}>
          ← 메인으로
        </button>
      )}
      {error && <div className="pending-approvals-error">{error}</div>}
      {message && <div className="pending-approvals-message">{message}</div>}
      {loading ? (
        <p>목록을 불러오는 중...</p>
      ) : list.length === 0 && !error ? (
        <p>승인 대기 중인 요청이 없습니다.</p>
      ) : !error ? (
        <>
          <table className="pending-approvals-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>요청자</th>
                <th>검색 조건 요약</th>
                <th>요청일시</th>
                <th>동작</th>
              </tr>
            </thead>
            <tbody>
              {list.map((row) => (
                <tr key={row.id}>
                  <td>{row.id}</td>
                  <td>{row.requester ?? '-'}</td>
                  <td className="pending-approvals-summary">{row.searchParamsSummary ?? '-'}</td>
                  <td>{row.requestedAt ?? '-'}</td>
                  <td>
                    <button
                      type="button"
                      className="pending-approvals-btn approve"
                      onClick={() => handleApprove(row.id)}
                      disabled={actionId === row.id}
                    >
                      {actionId === row.id ? '처리 중...' : '승인'}
                    </button>
                    <button
                      type="button"
                      className="pending-approvals-btn reject"
                      onClick={() => openRejectModal(row.id)}
                      disabled={actionId === row.id}
                    >
                      반려
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pending-approvals-pagination">
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
      ) : null}

      {rejectModal && (
        <div className="pending-approvals-modal-overlay" role="dialog" aria-labelledby="reject-modal-title">
          <div className="pending-approvals-modal">
            <h3 id="reject-modal-title">반려 사유 (선택)</h3>
            <textarea
              value={rejectModal.reason}
              onChange={(e) => setRejectModal((prev) => ({ ...prev, reason: e.target.value }))}
              placeholder="반려 사유를 입력하세요 (선택)"
              rows={3}
              className="pending-approvals-reason-input"
            />
            <div className="pending-approvals-modal-actions">
              <button type="button" className="back-button" onClick={closeRejectModal}>
                취소
              </button>
              <button type="button" className="pending-approvals-btn reject" onClick={handleRejectSubmit} disabled={actionId === rejectModal.id}>
                {actionId === rejectModal.id ? '처리 중...' : '반려'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PendingApprovals;
