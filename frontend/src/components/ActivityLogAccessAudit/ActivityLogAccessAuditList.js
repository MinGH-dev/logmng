import React, { useState, useEffect, useCallback, useRef } from 'react';
import DataTable, { EmptyTableBody } from '../DataTable';
import { getActivityLogAccessAudit } from '../../services/userActivityLogService';
import logger from '../../utils/logger';
import { getApiBaseUrl } from '../../config/runtimeApi';
import '../UserActivityLog/UserActivityLog.css';
import './ActivityLogAccessAuditList.css';

const getTodayStart = () => {
  const today = new Date();
  const y = today.getFullYear();
  const m = String(today.getMonth() + 1).padStart(2, '0');
  const d = String(today.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}T00:00`;
};

const getTodayEnd = () => {
  const today = new Date();
  const y = today.getFullYear();
  const m = String(today.getMonth() + 1).padStart(2, '0');
  const d = String(today.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}T23:59`;
};

const formatDateForAPI = (dateTimeLocal) => {
  if (!dateTimeLocal) return '';
  const [date, time] = dateTimeLocal.split('T');
  const seconds = time === '23:59' ? '59' : '00';
  return `${date} ${time}:${seconds}`;
};

const COLUMNS = [
  { key: 'accessorUserId', label: 'Accessor (user id)', sortable: true },
  { key: 'accessorDisplayName', label: 'Accessor name', sortable: false },
  { key: 'accessedAt', label: 'Timestamp', sortable: true },
  { key: 'targetActivityLogId', label: 'Target log id', sortable: true },
  { key: 'accessType', label: 'Access type', sortable: true },
];

/**
 * Activity log — access audit (sensitive detail / full copy views). Req 20260330 Screen 3.
 */
const ActivityLogAccessAuditList = ({
  initialTargetActivityLogId = null,
  onConsumedInitialTarget,
}) => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sortConfig, setSortConfig] = useState({ key: 'accessedAt', direction: 'desc' });

  const [startDate, setStartDate] = useState(getTodayStart);
  const [endDate, setEndDate] = useState(getTodayEnd);
  const [accessorUserId, setAccessorUserId] = useState('');
  const [targetActivityLogId, setTargetActivityLogId] = useState('');
  const consumedInitialRef = useRef(false);
  const skipFirstSortEffectRef = useRef(true);

  const fetchPage = useCallback(
    async (page, size, overrides = {}) => {
      const start = overrides.startDate != null ? overrides.startDate : startDate;
      const end = overrides.endDate != null ? overrides.endDate : endDate;
      const acc =
        overrides.accessorUserId !== undefined ? overrides.accessorUserId : accessorUserId;
      const tgt =
        overrides.targetActivityLogId !== undefined ? overrides.targetActivityLogId : targetActivityLogId;

      setLoading(true);
      setAuthError(null);
      try {
        const params = {
          startDate: formatDateForAPI(start),
          endDate: formatDateForAPI(end),
          page,
          pageSize: size,
          sortField: sortConfig.key === 'accessedAt' ? 'accessedAt' : sortConfig.key,
          sortDirection: sortConfig.direction,
        };
        const accTrim = String(acc).trim();
        if (accTrim !== '') {
          const n = Number(accTrim);
          if (!Number.isNaN(n)) params.accessorUserId = n;
        }
        const tgtTrim = String(tgt).trim();
        if (tgtTrim !== '') {
          const n = Number(tgtTrim);
          if (!Number.isNaN(n)) params.targetActivityLogId = n;
        }

        const result = await getActivityLogAccessAudit(params);
        if (result.success && result.data != null) {
          const payload = result.data;
          const list = Array.isArray(payload.data) ? payload.data : Array.isArray(payload) ? payload : [];
          setRows(list);
          const p = payload.pagination || {};
          setTotalPages(p.totalPages != null ? p.totalPages : 1);
          setTotalCount(p.totalCount != null ? p.totalCount : list.length);
          setCurrentPage(p.currentPage != null ? p.currentPage : page);
        } else if (result.status === 403 || result.code === 'ACCESS_AUDIT_FORBIDDEN' || result.code === 'FORBIDDEN') {
          setAuthError(result.error || '접근 감사 목록을 볼 권한이 없습니다.');
          setRows([]);
          setTotalPages(1);
          setTotalCount(0);
        } else {
          logger.error('접근 감사 조회 실패:', result);
          setAuthError(result.error || '조회에 실패했습니다.');
          setRows([]);
        }
      } catch (e) {
        logger.error('접근 감사 조회 예외:', e);
        setAuthError(e.message || '조회에 실패했습니다.');
        setRows([]);
      } finally {
        setLoading(false);
      }
    },
    [startDate, endDate, accessorUserId, targetActivityLogId, sortConfig],
  );

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
          const s = `${dateStr}T00:00`;
          const e = `${dateStr}T23:59`;
          setStartDate(s);
          setEndDate(e);
          const tid =
            initialTargetActivityLogId != null && initialTargetActivityLogId !== ''
              ? String(initialTargetActivityLogId)
              : '';
          if (tid) setTargetActivityLogId(tid);
          if (!consumedInitialRef.current && typeof onConsumedInitialTarget === 'function') {
            consumedInitialRef.current = true;
            onConsumedInitialTarget();
          }
          fetchPage(1, pageSize, {
            startDate: s,
            endDate: e,
            ...(tid ? { targetActivityLogId: tid } : {}),
          });
        } else {
          const tid =
            initialTargetActivityLogId != null && initialTargetActivityLogId !== ''
              ? String(initialTargetActivityLogId)
              : '';
          if (tid) setTargetActivityLogId(tid);
          if (!consumedInitialRef.current && typeof onConsumedInitialTarget === 'function') {
            consumedInitialRef.current = true;
            onConsumedInitialTarget();
          }
          fetchPage(1, pageSize, tid ? { targetActivityLogId: tid } : {});
        }
      })
      .catch(() => {
        const tid =
          initialTargetActivityLogId != null && initialTargetActivityLogId !== ''
            ? String(initialTargetActivityLogId)
            : '';
        if (tid) setTargetActivityLogId(tid);
        if (!consumedInitialRef.current && typeof onConsumedInitialTarget === 'function') {
          consumedInitialRef.current = true;
          onConsumedInitialTarget();
        }
        fetchPage(1, pageSize, { targetActivityLogId: tid });
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (skipFirstSortEffectRef.current) {
      skipFirstSortEffectRef.current = false;
      return;
    }
    setCurrentPage(1);
    fetchPage(1, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortConfig.key, sortConfig.direction]);

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setCurrentPage(1);
    fetchPage(1, pageSize);
  };

  const handleReset = () => {
    const s = getTodayStart();
    const e = getTodayEnd();
    setStartDate(s);
    setEndDate(e);
    setAccessorUserId('');
    setTargetActivityLogId('');
    setCurrentPage(1);
    setSortConfig({ key: 'accessedAt', direction: 'desc' });
    fetchPage(1, pageSize, { startDate: s, endDate: e, accessorUserId: '', targetActivityLogId: '' });
  };

  const formatTs = (x) => {
    if (!x) return '—';
    try {
      return new Date(x).toLocaleString('ko-KR');
    } catch {
      return String(x);
    }
  };

  const hasData = rows.length > 0;
  const pagination = {
    currentPage,
    totalPages,
    onPageChange: (p) => {
      setCurrentPage(p);
      fetchPage(p, pageSize);
    },
    simple: true,
    infoText: `Total ${totalCount.toLocaleString()} records`,
  };

  return (
    <div className="activity-log-access-audit-container activity-log-list-container">
      <div className="activity-log-header">
        <h1>Activity log — Access audit</h1>
        <p className="activity-log-description">
          Records access to sensitive activity detail and full copy content.
        </p>
      </div>

      <form
        className="activity-log-access-audit-form sf-compact-panel"
        onSubmit={handleSubmit}
        aria-label="Access audit filters"
      >
        <div id="activity-log-access-audit-filters-body">
          <div className="search-form-row-1">
            <div className="search-form-group">
              <label htmlFor="access-audit-start">Start date</label>
              <input
                id="access-audit-start"
                type="datetime-local"
                className="form-control"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                disabled={loading}
              />
            </div>
            <div className="search-form-group">
              <label htmlFor="access-audit-end">End date</label>
              <input
                id="access-audit-end"
                type="datetime-local"
                className="form-control"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                disabled={loading}
              />
            </div>
          </div>
          <div className="search-form-row-2">
            <div className="search-form-group">
              <label htmlFor="access-audit-accessor">Accessor user id (optional)</label>
              <input
                id="access-audit-accessor"
                type="text"
                inputMode="numeric"
                className="form-control"
                value={accessorUserId}
                onChange={(e) => setAccessorUserId(e.target.value)}
                disabled={loading}
                autoComplete="off"
              />
            </div>
            <div className="search-form-group">
              <label htmlFor="access-audit-target">Target activity log id (optional)</label>
              <input
                id="access-audit-target"
                type="text"
                inputMode="numeric"
                className="form-control"
                value={targetActivityLogId}
                onChange={(e) => setTargetActivityLogId(e.target.value)}
                disabled={loading}
                autoComplete="off"
              />
            </div>
          </div>
          <div className="search-form-actions">
            <button type="submit" className="btn btn-primary" disabled={loading}>
              Search
            </button>
            <button type="button" className="btn btn-secondary" onClick={handleReset} disabled={loading}>
              Reset
            </button>
          </div>
        </div>
      </form>

      {authError && (
        <div className="activity-log-auth-error" role="alert">
          {authError}
        </div>
      )}

      <div className="activity-log-results">
        <div className="results-header">
          <span className="results-count">{totalCount.toLocaleString()} records</span>
        </div>
        <div role="region" aria-label="Access audit table" aria-busy={loading}>
          <DataTable
            columns={COLUMNS}
            sortConfig={sortConfig}
            onSort={handleSort}
            loading={loading}
            emptyMessage="No access audit records."
            emptyColSpan={COLUMNS.length}
            ariaLabel="Access audit table"
            pagination={pagination}
            pageSize={pageSize}
            onPageSizeChange={(newSize) => {
              setPageSize(newSize);
              setCurrentPage(1);
              fetchPage(1, newSize);
            }}
          >
            {!hasData ? (
              <EmptyTableBody colSpan={COLUMNS.length} message="No access audit records." />
            ) : (
              rows.map((row) => (
                <tr key={row.id != null ? row.id : `${row.targetActivityLogId}-${row.accessedAt}`}>
                  <td>{row.accessorUserId != null ? row.accessorUserId : '—'}</td>
                  <td>{row.accessorDisplayName || row.accessor_display_name || '—'}</td>
                  <td>{formatTs(row.accessedAt ?? row.accessed_at)}</td>
                  <td>{row.targetActivityLogId != null ? row.targetActivityLogId : row.target_activity_log_id}</td>
                  <td>{row.accessType || row.access_type || '—'}</td>
                </tr>
              ))
            )}
          </DataTable>
        </div>
      </div>
    </div>
  );
};

export default ActivityLogAccessAuditList;
