import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  getSearchHistoryList,
  getSearchHistoryDetail,
  approveSearchHistory,
  rejectSearchHistory,
} from '../../services/searchHistoryService';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../../services/filterOptionsService';
import { getEmployeeNumberDisplay, getScreenFunctions, getSelfContext } from '../../utils/security';
import DataTable, { EmptyTableBody } from '../DataTable';
import UserContextFilterBlock from '../common/UserContextFilterBlock';
import logger from '../../utils/logger';
import '../SearchHistory/SearchHistory.css';
import './PendingApprovals.css';

const LIST_CONTEXT = 'pending-approvals';

const PA_COLUMNS = [
  { key: 'seq', label: '순번', sortable: true },
  { key: 'id', label: 'ID', sortable: true },
  { key: 'requesterDepartment', label: '부서', sortable: false },
  { key: 'requesterUsername', label: '사용자ID', sortable: false },
  { key: 'requesterDisplayName', label: '사용자명', sortable: false },
  { key: 'searchParamsSummary', label: '검색 조건 요약', sortable: false },
  { key: 'requested_at', label: '요청일시', sortable: true },
  { key: 'approvalStatus', label: '동작', sortable: false },
  { key: 'actions', label: '작업', sortable: false },
];

/** Grid labels (요건 20260407 §1) */
const PA_STATUS_LABEL = {
  PENDING: '승인대기',
  APPROVED: '승인',
  REJECTED: '반려',
  EXPIRED: '만료',
};

const PA_APPROVAL_STATUS_OPTIONS = [
  { value: 'PENDING', label: '승인대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '반려' },
  { value: 'EXPIRED', label: '만료' },
];

const FORBIDDEN_CODES = ['FORBIDDEN_NOT_APPROVER', 'NOT_APPROVER'];

const SCOPE_HINT_LABELS = {
  self: '표시: 본인',
  team: '표시: 부서',
  all: '표시: 전체',
};

const PA_APPROVAL_DROPDOWN_ID = 'pending-approvals-approval-dropdown-panel';
function shouldLogPendingApprovalVisibilityDiagnostics() {
  return process.env.NODE_ENV !== 'production'
    && process.env.REACT_APP_PA_DEBUG_VISIBILITY === 'true';
}

function getApprovalSummary(selected) {
  if (!Array.isArray(selected) || selected.length === 0) return '전체';
  if (selected.length === PA_APPROVAL_STATUS_OPTIONS.length) return '전체';
  return selected
    .map((v) => PA_APPROVAL_STATUS_OPTIONS.find((o) => o.value === v)?.label)
    .filter(Boolean)
    .join(', ');
}

function logPendingApprovalVisibilityDecision({
  rowId,
  canApprove,
  rawStatus,
  normalizedStatus,
  pendingRow,
}) {
  if (!shouldLogPendingApprovalVisibilityDiagnostics()) return;
  logger.debug('[pending-approvals][visibility]', {
    rowId,
    canApprove,
    rawStatus,
    normalizedStatus,
    pendingRow,
    showActions: canApprove && pendingRow,
  });
}

function normalizeApprovalStatus(status) {
  if (status == null) return '';
  const normalized = String(status).trim().toUpperCase();
  return normalized;
}

/** 동작 구분 multi-select (검색 이력과 동일 패턴; 라벨은 §1 승인대기/승인/반려/만료) */
function PendingApprovalsActionDropdown({ selected, onToggle, onSelectAll, id }) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  const allSelected = Array.isArray(selected) && selected.length === PA_APPROVAL_STATUS_OPTIONS.length;
  const summary = getApprovalSummary(selected);

  const close = useCallback(() => {
    setOpen(false);
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        close();
        containerRef.current?.querySelector('[data-pa-approval-trigger]')?.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, close]);

  useEffect(() => {
    if (!open) return;
    const onClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) close();
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, [open, close]);

  const handleTriggerKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      setOpen((o) => !o);
    }
  };

  return (
    <div className="search-history-approval-dropdown" ref={containerRef}>
      <div className="search-history-approval-dropdown__trigger-wrapper">
        <label id={`${id}-label`} className="search-history-approval-dropdown__label">
          동작 구분
        </label>
        <button
          type="button"
          data-pa-approval-trigger
          id={`${id}-trigger`}
          className="search-history-approval-dropdown__trigger"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-controls={PA_APPROVAL_DROPDOWN_ID}
          aria-label="동작 구분"
          aria-labelledby={`${id}-label`}
          aria-describedby={open ? undefined : `${id}-summary`}
          onClick={() => setOpen((o) => !o)}
          onKeyDown={handleTriggerKeyDown}
        >
          <span id={`${id}-summary`} className="search-history-approval-dropdown__summary">
            {summary}
          </span>
          <span className="search-history-approval-dropdown__chevron" aria-hidden="true">
            {open ? '▲' : '▼'}
          </span>
        </button>
      </div>
      {open && (
        <div
          id={PA_APPROVAL_DROPDOWN_ID}
          className="search-history-approval-dropdown__panel"
          role="listbox"
          aria-multiselectable="true"
          aria-labelledby={`${id}-label`}
        >
          <button
            type="button"
            role="option"
            aria-selected={allSelected}
            className="search-history-approval-dropdown__option search-history-approval-dropdown__select-all"
            onClick={onSelectAll}
          >
            <span className="search-history-approval-dropdown__checkbox" aria-hidden="true">
              {allSelected ? '☑' : '☐'}
            </span>
            모두선택
          </button>
          {PA_APPROVAL_STATUS_OPTIONS.map((opt) => {
            const isSelected = Array.isArray(selected) && selected.includes(opt.value);
            return (
              <button
                key={opt.value}
                type="button"
                role="option"
                aria-selected={isSelected}
                className="search-history-approval-dropdown__option"
                onClick={() => onToggle(opt.value)}
              >
                <span className="search-history-approval-dropdown__checkbox" aria-hidden="true">
                  {isSelected ? '☑' : '☐'}
                </span>
                {opt.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function toApiDatetime(localValue) {
  if (!localValue || typeof localValue !== 'string') return '';
  const s = localValue.trim();
  if (!s) return '';
  const normalized = s.replace('T', ' ');
  return normalized.length === 16 ? `${normalized}:00` : normalized.substring(0, 19);
}

function getDefaultRequestedAtFrom(daysAgo = 7) {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  d.setHours(0, 0, 0, 0);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}T00:00`;
}

function getDefaultRequestedAtTo() {
  const d = new Date();
  d.setHours(23, 59, 0, 0);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}T23:59`;
}

const createEmptyRequesterFilters = () => ({
  department: '',
  username: '',
  userId: '',
});

const getLockedRequesterFilters = (selfContext) => ({
  department: selfContext?.department || '',
  username: selfContext?.username || '',
  userId: getEmployeeNumberDisplay(selfContext),
  employeeNumber: selfContext?.employeeNumber || '',
});

function getRequesterCellValues(row) {
  const deptName = row.requesterDepartmentName ?? row.requester_department_name;
  const deptNameStr = deptName != null && String(deptName).trim() !== '' ? String(deptName).trim() : null;
  const deptCode = row.requesterDepartmentCode ?? row.requester_department_code ?? row.requesterDepartment ?? '';
  const department = deptNameStr ?? ((deptCode ? String(deptCode) : '') || '—');
  const username = row.requesterUsername ?? row.requester_username ?? '';
  const requesterUsername = username ? String(username) : '—';
  const displayName = row.requesterDisplayName ?? row.requester_display_name ?? '';
  const displayNameTrimmed = displayName != null && String(displayName).trim() !== '' ? String(displayName).trim() : null;
  const requesterDisplayName = displayNameTrimmed ?? ((username ? String(username) : '') || '—');
  return { department, requesterUsername, requesterDisplayName };
}

function SearchParamsDetailView({ searchParams }) {
  if (!searchParams || typeof searchParams !== 'object') return <p>검색 조건 없음</p>;
  return (
    <pre className="pending-approvals-detail-pre">
      {JSON.stringify(searchParams, null, 2)}
    </pre>
  );
}

const PendingApprovals = ({ user }) => {
  const screenFunctions = getScreenFunctions(user);
  const canApprove = screenFunctions?.['pending-approvals']?.approve === true;
  const scope = user?.screenScopes?.['pending-approvals'];
  const scopeHint = scope ? SCOPE_HINT_LABELS[scope] : null;
  const selfContext = useMemo(() => getSelfContext(user), [user]);
  const isSelfScope = !user?.isSystemAdmin && user?.screenScopes?.['pending-approvals'] === 'self';
  const lockedRequesterFilters = getLockedRequesterFilters(selfContext);

  const [list, setList] = useState([]);
  const [pagination, setPagination] = useState({ currentPage: 1, totalPages: 1, totalCount: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [departmentList, setDepartmentList] = useState([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sortConfig, setSortConfig] = useState({ key: 'requested_at', direction: 'desc' });
  const [actionId, setActionId] = useState(null);
  const [rejectModal, setRejectModal] = useState(null);

  const [requesterFilters, setRequesterFilters] = useState(
    () => (isSelfScope ? lockedRequesterFilters : createEmptyRequesterFilters()),
  );
  const [appliedRequesterFilters, setAppliedRequesterFilters] = useState(
    () => (isSelfScope ? lockedRequesterFilters : createEmptyRequesterFilters()),
  );
  const [requestedAtFrom, setRequestedAtFrom] = useState(() => getDefaultRequestedAtFrom(7));
  const [requestedAtTo, setRequestedAtTo] = useState(() => getDefaultRequestedAtTo());
  const [approvalStatuses, setApprovalStatuses] = useState([]);
  const [appliedRequestedAtFrom, setAppliedRequestedAtFrom] = useState(() => getDefaultRequestedAtFrom(7));
  const [appliedRequestedAtTo, setAppliedRequestedAtTo] = useState(() => getDefaultRequestedAtTo());
  const [appliedApprovalStatuses, setAppliedApprovalStatuses] = useState([]);

  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [detailData, setDetailData] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);

  const effectiveRequesterFilters = isSelfScope ? null : appliedRequesterFilters;

  const loadList = useCallback(async () => {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const params = {
        page,
        pageSize,
        sortField: sortConfig.key,
        sortDirection: sortConfig.direction,
        listContext: LIST_CONTEXT,
      };
      if (effectiveRequesterFilters) {
        const filters = effectiveRequesterFilters;
        params.department = filters.department ?? '';
        params.username = filters.username ?? '';
        const uid = filters.userId;
        const userIdNum = (uid !== '' && uid != null && uid !== undefined)
          ? (typeof uid === 'number' ? uid : Number(uid))
          : undefined;
        params.userId = (userIdNum !== undefined && !Number.isNaN(userIdNum)) ? userIdNum : (filters.userId ?? '');
      }
      if (appliedRequestedAtFrom && String(appliedRequestedAtFrom).trim()) {
        params.requestedAtFrom = toApiDatetime(appliedRequestedAtFrom);
      }
      if (appliedRequestedAtTo && String(appliedRequestedAtTo).trim()) {
        params.requestedAtTo = toApiDatetime(appliedRequestedAtTo);
      }
      if (Array.isArray(appliedApprovalStatuses) && appliedApprovalStatuses.length > 0) {
        params.approvalStatuses = appliedApprovalStatuses;
      }
      const result = await getSearchHistoryList(params);
      if (result.success && result.data) {
        setList(result.data.data || []);
        setPagination(result.data.pagination || { currentPage: 1, totalPages: 1, totalCount: 0 });
      }
    } catch (e) {
      logger.error('복호화 승인 관리 목록 조회 실패:', e);
      setError(e.message || '목록을 불러오지 못했습니다.');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, [
    effectiveRequesterFilters,
    page,
    pageSize,
    sortConfig.direction,
    sortConfig.key,
    appliedRequestedAtFrom,
    appliedRequestedAtTo,
    appliedApprovalStatuses,
  ]);

  useEffect(() => {
    if (isSelfScope) {
      const next = getLockedRequesterFilters(selfContext);
      setRequesterFilters(next);
      setAppliedRequesterFilters(next);
      setPage(1);
      setDepartmentList([]);
      return;
    }

    let cancelled = false;
    getDepartmentFilterOptions(FILTER_OPTION_SCREEN_IDS.PENDING_APPROVALS)
      .then((res) => {
        if (!cancelled && res.success && Array.isArray(res.data)) {
          setDepartmentList(res.data);
        }
      })
      .catch(() => {
        if (!cancelled) setDepartmentList([]);
      });

    return () => {
      cancelled = true;
    };
  }, [isSelfScope, selfContext]);

  useEffect(() => {
    loadList();
  }, [loadList]);

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

  const handleRequesterFilterChange = (name, value) => {
    setRequesterFilters((prev) => ({ ...prev, [name]: value }));
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (requestedAtFrom && requestedAtTo && requestedAtFrom > requestedAtTo) return;
    setAppliedRequesterFilters({ ...requesterFilters });
    setAppliedRequestedAtFrom(requestedAtFrom);
    setAppliedRequestedAtTo(requestedAtTo);
    setAppliedApprovalStatuses(approvalStatuses);
    setPage(1);
  };

  const handleReset = () => {
    const resetFilters = isSelfScope ? lockedRequesterFilters : createEmptyRequesterFilters();
    setRequesterFilters(resetFilters);
    setAppliedRequesterFilters(resetFilters);
    const fromDefault = getDefaultRequestedAtFrom(7);
    const toDefault = getDefaultRequestedAtTo();
    setRequestedAtFrom(fromDefault);
    setRequestedAtTo(toDefault);
    setApprovalStatuses([]);
    setAppliedRequestedAtFrom(fromDefault);
    setAppliedRequestedAtTo(toDefault);
    setAppliedApprovalStatuses([]);
    setPage(1);
  };

  const handleApprovalStatusToggle = (value) => {
    setApprovalStatuses((prev) =>
      (prev.includes(value) ? prev.filter((s) => s !== value) : [...prev, value]),
    );
  };

  const handleSelectAllApprovalStatuses = () => {
    const all = PA_APPROVAL_STATUS_OPTIONS.map((o) => o.value);
    setApprovalStatuses((prev) => (prev.length === all.length ? [] : all));
  };

  const handleDatePresetChange = (e) => {
    const days = Number(e.target.value);
    if (Number.isNaN(days) || days <= 0) return;
    const from = getDefaultRequestedAtFrom(days);
    const to = getDefaultRequestedAtTo();
    setRequestedAtFrom(from);
    setRequestedAtTo(to);
    setAppliedRequestedAtFrom(from);
    setAppliedRequestedAtTo(to);
    setPage(1);
  };

  const handleApprove = async (id) => {
    setActionId(id);
    setError(null);
    setMessage(null);
    try {
      await approveSearchHistory(id);
      setMessage('승인되었습니다.');
      await loadList();
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
      await loadList();
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

  const handleViewDetail = async (row) => {
    setDetailModalOpen(true);
    setDetailData(null);
    setDetailError(null);
    setDetailLoading(true);
    try {
      const result = await getSearchHistoryDetail(row.id, { listContext: LIST_CONTEXT });
      if (!result.success || !result.data) throw new Error('상세 조회 실패');
      setDetailData(result.data);
    } catch (e) {
      logger.error('복호화 승인 상세 조회 실패:', e);
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

  const dateRangeInvalid = !!(requestedAtFrom && requestedAtTo && requestedAtFrom > requestedAtTo);

  return (
    <div className="pending-approvals pending-approvals-list search-history-list">
      <h2>복호화 승인 관리</h2>
      {scopeHint && (
        <p className="pending-approvals-scope-hint" aria-live="polite">
          {scopeHint}
        </p>
      )}
      <form
        className="search-history-toolbar pending-approvals-toolbar sf-compact-panel"
        role="search"
        aria-label="복호화 승인 관리 조회 조건"
        onSubmit={handleSearchSubmit}
      >
        <div className="search-history-toolbar__row-1 sf-block" role="group" aria-label="요청일시">
          <div className="search-history-toolbar__date-row sf-row">
            <div className="search-history-field-group">
              <label htmlFor="pending-approvals-requested-at-from">요청일시 (시작)</label>
              <input
                id="pending-approvals-requested-at-from"
                type="datetime-local"
                className="form-control"
                value={requestedAtFrom}
                onChange={(e) => setRequestedAtFrom(e.target.value)}
                aria-describedby={dateRangeInvalid ? 'pending-approvals-date-range-err' : undefined}
              />
            </div>
            <div className="search-history-field-group">
              <label htmlFor="pending-approvals-requested-at-to">요청일시 (종료)</label>
              <input
                id="pending-approvals-requested-at-to"
                type="datetime-local"
                className="form-control"
                value={requestedAtTo}
                onChange={(e) => setRequestedAtTo(e.target.value)}
                aria-invalid={dateRangeInvalid}
                aria-describedby={dateRangeInvalid ? 'pending-approvals-date-range-err' : undefined}
              />
            </div>
            {dateRangeInvalid && (
              <span id="pending-approvals-date-range-err" className="search-history-field-error" role="alert">
                시작일시는 종료일시 이전이어야 합니다.
              </span>
            )}
            <div className="search-history-field-group search-history-date-preset">
              <label htmlFor="pending-approvals-date-preset">기간</label>
              <select
                id="pending-approvals-date-preset"
                className="form-control"
                defaultValue="7"
                onChange={handleDatePresetChange}
                aria-label="요청일시 기간 프리셋"
              >
                <option value="7">7d</option>
                <option value="15">15d</option>
                <option value="30">30d</option>
              </select>
            </div>
          </div>
        </div>
        <div className="search-history-toolbar__row-2 sf-block sf-row" role="group" aria-label="요청자·동작 구분">
          <UserContextFilterBlock
            blockLabel="요청자"
            mode={isSelfScope ? 'locked' : 'editable'}
            departmentList={departmentList}
            values={requesterFilters}
            lockedValues={lockedRequesterFilters}
            onChange={handleRequesterFilterChange}
            idPrefix="pending-approvals-requester"
            compact
            usernameMaxLength={5}
          />
          <div className="search-history-toolbar__extra">
            <PendingApprovalsActionDropdown
              id="pending-approvals-action"
              selected={approvalStatuses}
              onToggle={handleApprovalStatusToggle}
              onSelectAll={handleSelectAllApprovalStatuses}
            />
          </div>
          <div className="search-history-toolbar__actions" role="group" aria-label="검색 액션">
            <button
              type="submit"
              className="btn btn-primary sf-btn"
              disabled={loading || dateRangeInvalid}
              aria-busy={loading}
            >
              {loading ? '검색 중...' : '검색'}
            </button>
            <button type="button" className="btn btn-secondary sf-btn" onClick={handleReset}>
              초기화
            </button>
          </div>
        </div>
      </form>
      {error && <div className="search-history-error">{error}</div>}
      {message && <div className="pending-approvals-message">{message}</div>}
      <DataTable
        columns={PA_COLUMNS}
        sortConfig={sortConfig}
        onSort={handleSort}
        loading={loading}
        emptyMessage="조건에 맞는 복호화 승인 요청이 없습니다."
        emptyColSpan={9}
        ariaLabel="복호화 승인 관리 목록"
        pagination={{
          currentPage: pagination.currentPage || page,
          totalPages: pagination.totalPages || 1,
          onPageChange: (p) => setPage(p),
          simple: true,
          infoText: `총 ${pagination.totalCount}건`,
        }}
        pageSize={pageSize}
        onPageSizeChange={handlePageSizeChange}
      >
        {list.length === 0 ? (
          <EmptyTableBody colSpan={9} message="조건에 맞는 복호화 승인 요청이 없습니다." />
        ) : (
          list.map((row) => {
            const { department, requesterUsername, requesterDisplayName } = getRequesterCellValues(row);
            const rawStatus = row.approvalStatus ?? row.approval_status;
            const statusKey = normalizeApprovalStatus(rawStatus);
            const statusLabel = PA_STATUS_LABEL[statusKey] || statusKey || '—';
            const pendingRow = statusKey === 'PENDING';
            logPendingApprovalVisibilityDecision({
              rowId: row.id,
              canApprove,
              rawStatus,
              normalizedStatus: statusKey,
              pendingRow,
            });
            return (
              <tr key={row.id}>
                <td>{row.seq}</td>
                <td>{row.id}</td>
                <td>{department}</td>
                <td>{requesterUsername}</td>
                <td>{requesterDisplayName}</td>
                <td className="search-history-summary">{row.searchParamsSummary ?? '—'}</td>
                <td>{row.requestedAt ?? row.requested_at ?? '—'}</td>
                <td>{statusLabel}</td>
                <td>
                  <button
                    type="button"
                    className="search-history-btn detail"
                    onClick={() => handleViewDetail(row)}
                    aria-label={`상세, ID ${row.id}`}
                  >
                    상세
                  </button>
                  {canApprove && pendingRow && (
                    <>
                      <button
                        type="button"
                        className="pending-approvals-btn approve"
                        onClick={() => handleApprove(row.id)}
                        disabled={actionId === row.id}
                        aria-label={actionId === row.id ? '승인 처리 중' : `승인, 요청 ID ${row.id}`}
                      >
                        {actionId === row.id ? '처리 중...' : '승인'}
                      </button>
                      <button
                        type="button"
                        className="pending-approvals-btn reject"
                        onClick={() => openRejectModal(row.id)}
                        disabled={actionId === row.id}
                        aria-label={`반려, 요청 ID ${row.id}`}
                      >
                        반려
                      </button>
                    </>
                  )}
                </td>
              </tr>
            );
          })
        )}
      </DataTable>

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
              <button
                type="button"
                className="pending-approvals-btn reject"
                onClick={handleRejectSubmit}
                disabled={actionId === rejectModal.id}
                aria-label="반려 확인"
              >
                {actionId === rejectModal.id ? '처리 중...' : '반려'}
              </button>
            </div>
          </div>
        </div>
      )}

      {detailModalOpen && (
        <div
          className="search-history-detail-modal"
          onClick={handleDetailOverlayClick}
          role="dialog"
          aria-modal="true"
          aria-labelledby="pending-approvals-detail-title"
        >
          <div className="search-history-detail-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="search-history-detail-header">
              <h3 id="pending-approvals-detail-title">검색 이력 상세</h3>
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
                  {detailData.requestReason != null && String(detailData.requestReason).trim() !== '' && (
                    <div className="search-history-detail-row">
                      <span className="search-history-detail-key">요청 사유</span>
                      <span className="search-history-detail-value">{detailData.requestReason}</span>
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

export default PendingApprovals;
