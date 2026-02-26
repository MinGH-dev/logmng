import React, { useState, useEffect } from 'react';
import {
  getPendingList,
  approveSearchHistory,
  rejectSearchHistory,
} from '../../services/searchHistoryService';
import DataTable, { EmptyTableBody } from '../DataTable';
import logger from '../../utils/logger';
import './PendingApprovals.css';

const PENDING_COLUMNS = [
  { key: 'id', label: 'ID', sortable: false },
  { key: 'requester', label: '요청자', sortable: false },
  { key: 'searchParamsSummary', label: '검색 조건 요약', sortable: false },
  { key: 'requestedAt', label: '요청일시', sortable: false },
  { key: 'actions', label: '동작', sortable: false },
];

const FORBIDDEN_CODES = ['FORBIDDEN_NOT_APPROVER', 'NOT_APPROVER'];

const PendingApprovals = () => {
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
      {error && <div className="pending-approvals-error">{error}</div>}
      {message && <div className="pending-approvals-message">{message}</div>}
      {!error && (
        <DataTable
          columns={PENDING_COLUMNS}
          loading={loading}
          emptyMessage="승인 대기 중인 요청이 없습니다."
          emptyColSpan={5}
          ariaLabel="승인 대기 목록"
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
        >
          {list.length === 0 ? (
            <EmptyTableBody colSpan={5} message="승인 대기 중인 요청이 없습니다." />
          ) : (
            list.map((row) => (
              <tr key={row.id}>
                <td>{row.id}</td>
                <td>{row.requester ?? '-'}</td>
                <td className="pending-approvals-summary">{row.searchParamsSummary ?? '-'}</td>
                <td>{row.requestedAt ?? '-'}</td>
                <td>
                  <button type="button" className="pending-approvals-btn approve" onClick={() => handleApprove(row.id)} disabled={actionId === row.id} aria-label={actionId === row.id ? '승인 처리 중' : `승인, 요청 ID ${row.id}`}>
                    {actionId === row.id ? '처리 중...' : '승인'}
                  </button>
                  <button type="button" className="pending-approvals-btn reject" onClick={() => openRejectModal(row.id)} disabled={actionId === row.id} aria-label={`반려, 요청 ID ${row.id}`}>
                    반려
                  </button>
                </td>
              </tr>
            ))
          )}
        </DataTable>
      )}

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
