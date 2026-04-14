import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Box } from '@mui/material';
import DataTable, { EmptyTableBody } from '../DataTable';
import { searchAccessAudit } from '../../services/userActivityLogService';
import logger from '../../utils/logger';
import { getApiBaseUrl } from '../../config/runtimeApi';
import '../UserActivityLog/UserActivityLog.css';

const COLUMNS = [
  { key: 'id', label: 'ID', sortable: false },
  { key: 'accessor_user_id', label: '접근자 ID', sortable: false },
  { key: 'accessor_username', label: '접근자', sortable: false },
  { key: 'target_activity_log_id', label: '대상 활동 로그 ID', sortable: false },
  { key: 'access_type', label: '접근 유형', sortable: false },
  { key: 'created_at', label: '일시', sortable: true },
  { key: 'ip_address', label: 'IP', sortable: false },
];

const formatCell = (row, key) => {
  const v = row[key];
  if (v === undefined || v === null || v === '') return '-';
  return String(v);
};

const formatDateTime = (raw) => {
  if (!raw) return '-';
  try {
    return new Date(raw).toLocaleString('ko-KR');
  } catch {
    return String(raw);
  }
};

/**
 * 활동 로그 민감 상세/특권 열람 접근 감사 목록 (GET /api/activity-log/access-audit).
 */
const ActivityLogAccessAuditList = ({
  initialTargetActivityLogId = null,
  onConsumedInitialTarget,
}) => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [authError, setAuthError] = useState(null);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [accessorUserId, setAccessorUserId] = useState('');
  const [accessType, setAccessType] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sortConfig, setSortConfig] = useState({ key: 'created_at', direction: 'desc' });

  const deepLinkTargetRef = useRef(initialTargetActivityLogId);
  const consumedDeepLinkRef = useRef(false);

  useEffect(() => {
    deepLinkTargetRef.current = initialTargetActivityLogId;
    if (initialTargetActivityLogId != null) {
      consumedDeepLinkRef.current = false;
    }
  }, [initialTargetActivityLogId]);

  const fetchRows = useCallback(
    async (pageArg, overrides = {}) => {
      const page = pageArg ?? currentPage;
      const sortDirection = overrides.sortDirection ?? (sortConfig.direction === 'asc' ? 'asc' : 'desc');

      const tid = deepLinkTargetRef.current;
      const includeTarget =
        tid != null && !consumedDeepLinkRef.current && overrides.includeDeepLinkTarget !== false;

      const params = {
        startDate: overrides.startDate ?? startDate,
        endDate: overrides.endDate ?? endDate,
        page,
        pageSize: overrides.pageSize ?? pageSize,
        sortDirection,
      };

      const aid = (overrides.accessorUserId ?? accessorUserId).trim();
      if (aid !== '') {
        const n = Number(aid);
        if (!Number.isNaN(n)) params.accessorUserId = n;
      }

      const at = (overrides.accessType ?? accessType).trim();
      if (at !== '') params.accessType = at;

      if (includeTarget && tid != null) {
        params.targetActivityLogId = tid;
      }

      setLoading(true);
      setAuthError(null);

      try {
        const result = await searchAccessAudit(params);

        if (result.success && result.data) {
          setRows(result.data.data || []);
          setTotalPages(result.data.pagination?.totalPages || 1);
          setTotalCount(result.data.pagination?.totalCount || 0);

          if (params.targetActivityLogId != null) {
            consumedDeepLinkRef.current = true;
            deepLinkTargetRef.current = null;
            if (typeof onConsumedInitialTarget === 'function') {
              onConsumedInitialTarget();
            }
          }
        } else if (
          result.code === 'UNAUTHORIZED' ||
          result.code === 'ACCESS_AUDIT_FORBIDDEN' ||
          result.code === 'FORBIDDEN' ||
          (result.error && (String(result.error).includes('로그인') || String(result.error).includes('권한')))
        ) {
          setAuthError(result.error || '이 목록을 조회할 권한이 없습니다.');
          setRows([]);
          setTotalPages(1);
          setTotalCount(0);
        } else {
          logger.error('접근 감사 목록 응답 오류:', result);
          setAuthError(null);
          setRows([]);
          setTotalPages(1);
          setTotalCount(0);
        }
      } catch (e) {
        logger.error('접근 감사 목록 요청 실패:', e);
        setAuthError(null);
        setRows([]);
        setTotalPages(1);
        setTotalCount(0);
      } finally {
        setLoading(false);
      }
    },
    [
      accessType,
      accessorUserId,
      currentPage,
      endDate,
      onConsumedInitialTarget,
      pageSize,
      sortConfig.direction,
      startDate,
    ],
  );

  useEffect(() => {
    let cancelled = false;
    fetch(`${getApiBaseUrl()}/health`, { credentials: 'include' })
      .then((res) => res.json())
      .then((res) => {
        if (cancelled) return;
        const dateStr =
          res.success && res.data && res.data.timestamp ? String(res.data.timestamp).slice(0, 10) : null;
        const d =
          dateStr ||
          (() => {
            const t = new Date();
            const y = t.getFullYear();
            const m = String(t.getMonth() + 1).padStart(2, '0');
            const day = String(t.getDate()).padStart(2, '0');
            return `${y}-${m}-${day}`;
          })();
        setStartDate(d);
        setEndDate(d);
        fetchRows(1, { startDate: d, endDate: d, includeDeepLinkTarget: true });
      })
      .catch(() => {
        if (cancelled) return;
        const t = new Date();
        const y = t.getFullYear();
        const m = String(t.getMonth() + 1).padStart(2, '0');
        const day = String(t.getDate()).padStart(2, '0');
        const d = `${y}-${m}-${day}`;
        setStartDate(d);
        setEndDate(d);
        fetchRows(1, { startDate: d, endDate: d, includeDeepLinkTarget: true });
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setCurrentPage(1);
    fetchRows(1);
  };

  const handlePageChange = (page) => {
    setCurrentPage(page);
    fetchRows(page);
  };

  const handlePageSizeChange = (newSize) => {
    setPageSize(newSize);
    setCurrentPage(1);
    fetchRows(1, { pageSize: newSize });
  };

  const handleSort = (key) => {
    if (key !== 'created_at') return;
    const nextDir = sortConfig.key === key && sortConfig.direction === 'desc' ? 'asc' : 'desc';
    setSortConfig({ key, direction: nextDir });
    setCurrentPage(1);
    fetchRows(1, { sortDirection: nextDir });
  };

  const hasData = rows && rows.length > 0;
  const emptyMessage = '조회된 접근 감사 이력이 없습니다.';
  const pagination = {
    currentPage,
    totalPages,
    onPageChange: handlePageChange,
    simple: true,
    infoText: `총 ${totalCount.toLocaleString()}건`,
  };

  return (
    <Box className="activity-log-list-container" component="main">
      <div className="activity-log-header">
        <h1>활동 로그 접근 감사</h1>
        <p className="activity-log-description">
          민감 상세·특권 열람 등 대상 활동 로그에 대한 접근 기록을 조회합니다.
        </p>
      </div>

      <form className="activity-log-search-form sf-compact-panel" onSubmit={handleSearchSubmit} aria-label="접근 감사 검색">
        <div className="search-form-row-1">
          <div className="form-group">
            <label htmlFor="access-audit-start">시작일</label>
            <input
              id="access-audit-start"
              type="date"
              className="form-control"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
          <div className="form-group">
            <label htmlFor="access-audit-end">종료일</label>
            <input
              id="access-audit-end"
              type="date"
              className="form-control"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>
          <div className="form-group">
            <label htmlFor="access-audit-accessor">접근자 사용자 ID</label>
            <input
              id="access-audit-accessor"
              type="text"
              inputMode="numeric"
              className="form-control"
              value={accessorUserId}
              onChange={(e) => setAccessorUserId(e.target.value)}
              placeholder="선택"
            />
          </div>
          <div className="form-group">
            <label htmlFor="access-audit-type">접근 유형</label>
            <input
              id="access-audit-type"
              type="text"
              className="form-control"
              value={accessType}
              onChange={(e) => setAccessType(e.target.value)}
              placeholder="선택"
            />
          </div>
          <div className="form-group">
            <button type="submit" className="btn btn-primary" disabled={loading}>
              조회
            </button>
          </div>
        </div>
      </form>

      {authError && (
        <div className="activity-log-auth-error" role="alert">
          {authError}
        </div>
      )}

      <div className="activity-log-results" role="region" aria-label="접근 감사 결과">
        <DataTable
          columns={COLUMNS}
          sortConfig={sortConfig}
          onSort={handleSort}
          loading={loading}
          emptyMessage={emptyMessage}
          emptyColSpan={COLUMNS.length}
          ariaLabel="접근 감사 테이블"
          pagination={pagination}
          pageSize={pageSize}
          onPageSizeChange={handlePageSizeChange}
        >
          {!hasData ? (
            <EmptyTableBody colSpan={COLUMNS.length} message={emptyMessage} />
          ) : (
            rows.map((row) => (
              <tr key={row.id ?? `${row.accessor_user_id}-${row.created_at}-${row.target_activity_log_id}`}>
                <td>{formatCell(row, 'id')}</td>
                <td>{formatCell(row, 'accessor_user_id')}</td>
                <td>{formatCell(row, 'accessor_username')}</td>
                <td>{formatCell(row, 'target_activity_log_id')}</td>
                <td>{formatCell(row, 'access_type')}</td>
                <td>{formatDateTime(row.created_at)}</td>
                <td>{formatCell(row, 'ip_address')}</td>
              </tr>
            ))
          )}
        </DataTable>
      </div>
    </Box>
  );
};

export default ActivityLogAccessAuditList;
