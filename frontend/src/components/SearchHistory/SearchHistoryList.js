import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  getSearchHistoryList,
  reRequestSearchHistory,
  getSearchHistoryDetail,
} from '../../services/searchHistoryService';
import {
  FILTER_OPTION_SCREEN_IDS,
  getDepartmentFilterOptions,
} from '../../services/filterOptionsService';
import { getSelfContext } from '../../utils/security';
import DataTable, { EmptyTableBody } from '../DataTable';
import UserContextFilterBlock from '../common/UserContextFilterBlock';
import './SearchHistory.css';
import logger from '../../utils/logger';

const SEARCH_HISTORY_COLUMNS = [
  { key: 'seq', label: '순번', sortable: true },
  { key: 'requested_at', label: '검색일시', sortable: true },
  { key: 'requesterDepartment', label: '부서', sortable: false },
  { key: 'requesterUsername', label: '사용자ID', sortable: false },
  { key: 'requesterDisplayName', label: '사용자명', sortable: false },
  { key: 'searchConditions', label: '검색 조건', sortable: false },
  { key: 'searchResultCount', label: '검색건수', sortable: false },
  { key: 'decryptionTargetCount', label: '암호화건수', sortable: false },
  { key: 'approvalStatus', label: '복호화', sortable: false },
  { key: 'requestReason', label: '요청사유', sortable: false },
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

const APPROVAL_STATUS_OPTIONS = [
  { value: 'PENDING', label: '대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '반려' },
  { value: 'EXPIRED', label: '만료' },
];

const APPROVAL_DROPDOWN_ID = 'search-history-approval-dropdown';

/** Summary text for selected approval statuses (e.g. "전체", "대기, 승인") */
function getApprovalSummary(selected) {
  if (!Array.isArray(selected) || selected.length === 0) return '전체';
  if (selected.length === APPROVAL_STATUS_OPTIONS.length) return '전체';
  return selected
    .map((v) => APPROVAL_STATUS_OPTIONS.find((o) => o.value === v)?.label)
    .filter(Boolean)
    .join(', ');
}

/** Dropdown for 복호화 승인 여부: trigger + listbox with checkboxes (multi-select, select-all). Keyboard and ARIA per req 20260317. */
function ApprovalStatusDropdown({ selected, onToggle, onSelectAll, id }) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const listRef = useRef(null);
  const [focusedIndex, setFocusedIndex] = useState(-1);

  const allSelected = Array.isArray(selected) && selected.length === APPROVAL_STATUS_OPTIONS.length;
  const summary = getApprovalSummary(selected);

  const close = useCallback(() => {
    setOpen(false);
    setFocusedIndex(-1);
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        close();
        containerRef.current?.querySelector('[data-approval-trigger]')?.focus();
        return;
      }
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setFocusedIndex((i) => (i < APPROVAL_STATUS_OPTIONS.length ? i + 1 : i));
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setFocusedIndex((i) => (i > 0 ? i - 1 : 0));
        return;
      }
      if (e.key === ' ' && focusedIndex >= 0 && focusedIndex < APPROVAL_STATUS_OPTIONS.length) {
        e.preventDefault();
        const opt = APPROVAL_STATUS_OPTIONS[focusedIndex];
        onToggle(opt.value);
        return;
      }
      if (e.key === ' ' && focusedIndex === APPROVAL_STATUS_OPTIONS.length) {
        e.preventDefault();
        onSelectAll();
        return;
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, close, focusedIndex, onToggle, onSelectAll]);

  useEffect(() => {
    if (open && listRef.current && focusedIndex >= 0) {
      const options = listRef.current.querySelectorAll('[role="option"]');
      const el = options[focusedIndex];
      if (el) el.focus();
    }
  }, [open, focusedIndex]);

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
      if (!open) setFocusedIndex(0);
    }
  };

  return (
    <div className="search-history-approval-dropdown" ref={containerRef}>
      <div className="search-history-approval-dropdown__trigger-wrapper">
        <label id={`${id}-label`} className="search-history-approval-dropdown__label">
          복호화
        </label>
        <button
          type="button"
          data-approval-trigger
          id={`${id}-trigger`}
          className="search-history-approval-dropdown__trigger"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-controls={APPROVAL_DROPDOWN_ID}
          aria-label="복호화"
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
          id={APPROVAL_DROPDOWN_ID}
          ref={listRef}
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
            onKeyDown={(e) => {
              if (e.key === ' ') e.preventDefault();
            }}
          >
            <span className="search-history-approval-dropdown__checkbox" aria-hidden="true">
              {allSelected ? '☑' : '☐'}
            </span>
            모두선택
          </button>
          {APPROVAL_STATUS_OPTIONS.map((opt, idx) => {
            const isSelected = Array.isArray(selected) && selected.includes(opt.value);
            return (
              <button
                key={opt.value}
                type="button"
                role="option"
                aria-selected={isSelected}
                className="search-history-approval-dropdown__option"
                onClick={() => onToggle(opt.value)}
                onKeyDown={(e) => {
                  if (e.key === ' ') e.preventDefault();
                }}
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

/** datetime-local value (yyyy-MM-ddThh:mm) → API format yyyy-MM-dd HH:mm:ss */
function toApiDatetime(localValue) {
  if (!localValue || typeof localValue !== 'string') return '';
  const s = localValue.trim();
  if (!s) return '';
  const normalized = s.replace('T', ' ');
  return normalized.length === 16 ? `${normalized}:00` : normalized.substring(0, 19);
}

/** Default 검색일시(시작): today − N days at 00:00, datetime-local format (req 20260317). */
function getDefaultRequestedAtFrom(daysAgo = 7) {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  d.setHours(0, 0, 0, 0);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}T00:00`;
}

/** Default 검색일시(종료): today at 23:59, datetime-local format (req 20260317). */
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
  userId: selfContext?.userId || '',
});

/** Requester column values from row; supports camelCase and snake_case for API compatibility. */
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

/** List/detail: camelCase or snake_case from API. */
function getSearchResultTotalCount(rowOrDetail) {
  if (!rowOrDetail || typeof rowOrDetail !== 'object') return null;
  const v = rowOrDetail.searchResultTotalCount ?? rowOrDetail.search_result_total_count;
  if (v == null) return null;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

function getDecryptionTargetCount(rowOrDetail) {
  if (!rowOrDetail || typeof rowOrDetail !== 'object') return null;
  const v = rowOrDetail.decryptionTargetCount ?? rowOrDetail.decryption_target_count;
  if (v == null) return null;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

/** List cell: numeric only; null/undefined → 미집계 */
function formatListCount(value) {
  if (value == null) return '미집계';
  return String(value);
}

const SEARCH_PARAMS_LABELS = {
  startDate: '시작일시',
  endDate: '종료일시',
  application: '애플리케이션',
  servicegroup: '서비스 그룹',
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

/** Modal summary: 검색건수 and 암호화건수 from detailData. */
function SearchHistoryDetailCounts({ detailData }) {
  if (!detailData) return null;
  const searchTotal = getSearchResultTotalCount(detailData);
  const decryptTarget = getDecryptionTargetCount(detailData);
  if (searchTotal == null && decryptTarget == null) return null;
  return (
    <div
      className="search-history-detail-summary-counts"
      aria-label="검색건수·암호화건수"
    >
      {searchTotal != null && (
        <div className="search-history-detail-summary-counts__line">
          검색건수: {searchTotal}
        </div>
      )}
      {decryptTarget != null && (
        <div className="search-history-detail-summary-counts__line">
          암호화건수: {decryptTarget}
        </div>
      )}
    </div>
  );
}

/** Section "복호화 요청 대상 (총 n건)" with table when APPROVED; "해당 없음" otherwise. n = decryption count only (never search total). Req 20260318-search-history-detail-modal-decryption-list. */
function DecryptionRequestedSection({ detailData }) {
  if (!detailData) return null;
  const rows = detailData.decryptionRequestedRows;
  const hasRows = Array.isArray(rows) && rows.length > 0;
  const decryptionN = getDecryptionTargetCount(detailData) ?? detailData.decryptionRequestedCount ?? (hasRows ? rows.length : 0);
  const count = hasRows ? decryptionN : 0;
  const sectionId = 'search-history-decryption-requested-title';

  if (!hasRows) {
    return (
      <section className="search-history-decryption-requested-section" aria-label="복호화 요청 대상">
        <p className="search-history-decryption-requested-none">복호화 요청 대상: 해당 없음</p>
      </section>
    );
  }

  return (
    <section className="search-history-decryption-requested-section" aria-labelledby={sectionId}>
      <h4 id={sectionId} className="search-history-decryption-requested-title">
        복호화 요청 대상 (총 {count}건)
      </h4>
      <div className="search-history-decryption-requested-wrapper">
        <table
          className="search-history-decryption-requested-table"
          aria-labelledby={sectionId}
        >
          <thead>
            <tr>
              <th scope="col">애플리케이션</th>
              <th scope="col">서비스 그룹</th>
              <th scope="col">GUID</th>
              <th scope="col">status</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, idx) => (
              <tr key={row.guid != null ? `${String(row.guid)}::${row.status != null ? String(row.status) : ''}::${idx}` : idx}>
                <td>{row.application != null && String(row.application).trim() !== '' ? String(row.application) : '—'}</td>
                <td>{row.serviceGroup != null && String(row.serviceGroup).trim() !== '' ? String(row.serviceGroup) : '—'}</td>
                <td>{row.guid != null ? String(row.guid) : '—'}</td>
                <td>{row.status != null && String(row.status).trim() !== '' ? String(row.status) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
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

const SearchHistoryList = ({ onBackToMain, onReSearch, user }) => {
  const isSelfScope = !user?.isSystemAdmin && user?.screenScopes?.['search-history'] === 'self';
  const selfContext = useMemo(() => getSelfContext(user), [user]);
  const lockedRequesterFilters = getLockedRequesterFilters(selfContext);
  const [list, setList] = useState([]);
  const [pagination, setPagination] = useState({ currentPage: 1, totalPages: 1, totalCount: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [departmentList, setDepartmentList] = useState([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sortConfig, setSortConfig] = useState({ key: 'requested_at', direction: 'desc' });
  const [requesterFilters, setRequesterFilters] = useState(
    () => (isSelfScope ? lockedRequesterFilters : createEmptyRequesterFilters()),
  );
  const [appliedRequesterFilters, setAppliedRequesterFilters] = useState(
    () => (isSelfScope ? lockedRequesterFilters : createEmptyRequesterFilters()),
  );
  const [requestedAtFrom, setRequestedAtFrom] = useState(() => getDefaultRequestedAtFrom(7));
  const [requestedAtTo, setRequestedAtTo] = useState(() => getDefaultRequestedAtTo());
  const [approvalStatuses, setApprovalStatuses] = useState([]);
  const [requestReason, setRequestReason] = useState('');
  const [appliedRequestedAtFrom, setAppliedRequestedAtFrom] = useState(() => getDefaultRequestedAtFrom(7));
  const [appliedRequestedAtTo, setAppliedRequestedAtTo] = useState(() => getDefaultRequestedAtTo());
  const [appliedApprovalStatuses, setAppliedApprovalStatuses] = useState([]);
  const [appliedRequestReason, setAppliedRequestReason] = useState('');
  const [reRequestingId, setReRequestingId] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [detailData, setDetailData] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);
  const [conditionsModalOpen, setConditionsModalOpen] = useState(false);
  const [conditionsModalData, setConditionsModalData] = useState(null);
  const [conditionsModalLoading, setConditionsModalLoading] = useState(false);
  const [conditionsModalError, setConditionsModalError] = useState(null);

  const effectiveRequesterFilters = isSelfScope ? null : appliedRequesterFilters;

  const loadList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {
        page,
        pageSize,
        sortField: sortConfig.key,
        sortDirection: sortConfig.direction,
      };
      if (effectiveRequesterFilters) {
        const filters = effectiveRequesterFilters;
        params.department = filters.department ?? '';
        params.username = filters.username ?? '';
        const uid = filters.userId;
        const userIdNum = (uid !== '' && uid != null && uid !== undefined) ? (typeof uid === 'number' ? uid : Number(uid)) : undefined;
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
      if (appliedRequestReason != null && String(appliedRequestReason).trim() !== '') {
        params.requestReason = String(appliedRequestReason).trim();
      }
      const result = await getSearchHistoryList(params);
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
  }, [effectiveRequesterFilters, page, pageSize, sortConfig.direction, sortConfig.key, appliedRequestedAtFrom, appliedRequestedAtTo, appliedApprovalStatuses, appliedRequestReason]);

  useEffect(() => {
    if (isSelfScope) {
      const nextLockedRequesterFilters = getLockedRequesterFilters(selfContext);
      setRequesterFilters(nextLockedRequesterFilters);
      setAppliedRequesterFilters(nextLockedRequesterFilters);
      setPage(1);
      setDepartmentList([]);
      return;
    }

    let cancelled = false;
    getDepartmentFilterOptions(FILTER_OPTION_SCREEN_IDS.SEARCH_HISTORY)
      .then((res) => {
        if (!cancelled && res.success && Array.isArray(res.data)) {
          setDepartmentList(res.data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setDepartmentList([]);
        }
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
    setRequesterFilters((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setAppliedRequesterFilters({ ...requesterFilters });
    setAppliedRequestedAtFrom(requestedAtFrom);
    setAppliedRequestedAtTo(requestedAtTo);
    setAppliedApprovalStatuses(approvalStatuses);
    setAppliedRequestReason(requestReason);
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
    setRequestReason('');
    setAppliedRequestedAtFrom(fromDefault);
    setAppliedRequestedAtTo(toDefault);
    setAppliedApprovalStatuses([]);
    setAppliedRequestReason('');
    setPage(1);
  };

  const handleApprovalStatusToggle = (value) => {
    setApprovalStatuses((prev) =>
      prev.includes(value) ? prev.filter((s) => s !== value) : [...prev, value],
    );
  };

  const handleSelectAllApprovalStatuses = () => {
    const all = APPROVAL_STATUS_OPTIONS.map((o) => o.value);
    setApprovalStatuses((prev) => (prev.length === all.length ? [] : all));
  };

  /** 7d/15d/30d preset: set start to d−N, end to d+0, apply and refresh list (keep other filters). req 20260317 */
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

  const handleReRequest = async (id) => {
    setReRequestingId(id);
    try {
      await reRequestSearchHistory(id);
      await loadList();
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

  const handleOpenConditionsModal = async (row) => {
    setConditionsModalOpen(true);
    setConditionsModalData(null);
    setConditionsModalError(null);
    setConditionsModalLoading(true);
    try {
      const result = await getSearchHistoryDetail(row.id);
      if (!result.success || !result.data) throw new Error('상세 조회 실패');
      setConditionsModalData(result.data);
    } catch (e) {
      logger.error('검색 조건 조회 실패:', e);
      setConditionsModalError(e.message || '검색 조건을 불러오지 못했습니다.');
    } finally {
      setConditionsModalLoading(false);
    }
  };

  const closeConditionsModal = () => {
    setConditionsModalOpen(false);
    setConditionsModalData(null);
    setConditionsModalError(null);
  };

  const handleConditionsOverlayClick = (e) => {
    if (e.target === e.currentTarget) closeConditionsModal();
  };

  useEffect(() => {
    if (!detailModalOpen) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') closeDetailModal();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [detailModalOpen]);

  useEffect(() => {
    if (!conditionsModalOpen) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') closeConditionsModal();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [conditionsModalOpen]);

  return (
    <div className="search-history-list">
      <h2>검색 이력 (복호화 승인)</h2>
      <form
        className="search-history-toolbar sf-compact-panel"
        role="search"
        aria-label="검색 이력 조회 조건"
        onSubmit={handleSearchSubmit}
      >
        <div className="search-history-toolbar__row-1 sf-block" role="group" aria-label="검색일시">
          <div className="search-history-toolbar__date-row sf-row">
            <div className="search-history-field-group">
              <label htmlFor="search-history-requested-at-from">검색일시 (시작)</label>
              <input
                id="search-history-requested-at-from"
                type="datetime-local"
                className="form-control"
                value={requestedAtFrom}
                onChange={(e) => setRequestedAtFrom(e.target.value)}
                aria-describedby={requestedAtFrom && requestedAtTo && requestedAtFrom > requestedAtTo ? 'search-history-date-range-err' : undefined}
              />
            </div>
            <div className="search-history-field-group">
              <label htmlFor="search-history-requested-at-to">검색일시 (종료)</label>
              <input
                id="search-history-requested-at-to"
                type="datetime-local"
                className="form-control"
                value={requestedAtTo}
                onChange={(e) => setRequestedAtTo(e.target.value)}
                aria-invalid={!!(requestedAtFrom && requestedAtTo && requestedAtFrom > requestedAtTo)}
                aria-describedby={requestedAtFrom && requestedAtTo && requestedAtFrom > requestedAtTo ? 'search-history-date-range-err' : undefined}
              />
            </div>
            {requestedAtFrom && requestedAtTo && requestedAtFrom > requestedAtTo && (
              <span id="search-history-date-range-err" className="search-history-field-error" role="alert">
                시작일시는 종료일시 이전이어야 합니다.
              </span>
            )}
            <div className="search-history-field-group search-history-date-preset">
              <label htmlFor="search-history-date-preset">기간</label>
              <select
                id="search-history-date-preset"
                className="form-control"
                defaultValue="7"
                onChange={handleDatePresetChange}
                aria-label="검색일시 기간 프리셋"
              >
                <option value="7">7d</option>
                <option value="15">15d</option>
                <option value="30">30d</option>
              </select>
            </div>
          </div>
        </div>
        <div className="search-history-toolbar__row-2 sf-block sf-row" role="group" aria-label="요청자·복호화 승인 여부·요청 사유">
          <UserContextFilterBlock
            blockLabel="요청자"
            mode={isSelfScope ? 'locked' : 'editable'}
            departmentList={departmentList}
            values={requesterFilters}
            lockedValues={lockedRequesterFilters}
            onChange={handleRequesterFilterChange}
            idPrefix="search-history-requester"
            compact
            usernameMaxLength={5}
          />
          <div className="search-history-toolbar__extra">
            <ApprovalStatusDropdown
              id="search-history-approval"
              selected={approvalStatuses}
              onToggle={handleApprovalStatusToggle}
              onSelectAll={handleSelectAllApprovalStatuses}
            />
          </div>
          <div className="search-history-field-group">
            <label htmlFor="search-history-request-reason">요청 사유</label>
            <input
              id="search-history-request-reason"
              type="text"
              className="form-control"
              value={requestReason}
              onChange={(e) => setRequestReason(e.target.value)}
              placeholder="요청사유 부분 검색"
            />
          </div>
          <div className="search-history-toolbar__actions" role="group" aria-label="검색 액션">
            <button type="submit" className="btn btn-primary sf-btn" disabled={loading} aria-busy={loading}>
              {loading ? '검색 중...' : '검색'}
            </button>
            <button type="button" className="btn btn-secondary sf-btn" onClick={handleReset}>
              초기화
            </button>
          </div>
        </div>
      </form>
      {error && <div className="search-history-error">{error}</div>}
      <DataTable
        columns={SEARCH_HISTORY_COLUMNS}
        sortConfig={sortConfig}
        onSort={handleSort}
        loading={loading}
        emptyMessage="검색 이력이 없습니다. 복호화 승인 요청을 한 검색이 여기에 표시됩니다."
        emptyColSpan={12}
        ariaLabel="검색 이력 목록"
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
          <EmptyTableBody colSpan={12} message="검색 이력이 없습니다. 복호화 승인 요청을 한 검색이 여기에 표시됩니다." />
        ) : (
          list.map((row) => {
            const isRequester = user && (Number(row.userId) === Number(user.id) || row.requesterUsername === user.username);
            const { department, requesterUsername, requesterDisplayName } = getRequesterCellValues(row);
            const requestReasonText = row.requestReason ?? row.request_reason ?? '';
            const searchTotal = getSearchResultTotalCount(row);
            const decryptTarget = getDecryptionTargetCount(row);
            return (
              <tr key={row.id}>
                <td>{row.seq}</td>
                <td>{row.requestedAt}</td>
                <td>{department}</td>
                <td>{requesterUsername}</td>
                <td>{requesterDisplayName}</td>
                <td>
                  <button
                    type="button"
                    className="search-history-btn conditions-trigger"
                    onClick={() => handleOpenConditionsModal(row)}
                    aria-label={`검색 조건 보기, ID ${row.id}`}
                  >
                    검색 조건 보기
                  </button>
                </td>
                <td className="search-history-counts-cell">{formatListCount(searchTotal)}</td>
                <td className="search-history-counts-cell">{formatListCount(decryptTarget)}</td>
                <td>{STATUS_LABEL[row.approvalStatus] || row.approvalStatus}</td>
                <td className="search-history-summary">{requestReasonText || '—'}</td>
                <td>{row.expiresAt}</td>
                <td>
                  {isRequester && (
                    <button type="button" className="search-history-btn re-search" onClick={() => handleReSearch(row)} aria-label={`재조회, ID ${row.id}`}>재조회</button>
                  )}
                  {isRequester && (
                    <button type="button" className="search-history-btn detail" onClick={() => handleViewDetail(row)} aria-label={`자세히 보기, ID ${row.id}`}>자세히 보기</button>
                  )}
                  {isRequester && row.isExpired && (
                    <button type="button" className="search-history-btn re-request" onClick={() => handleReRequest(row.id)} disabled={reRequestingId === row.id} aria-label={reRequestingId === row.id ? '재요청 처리 중' : `재요청, ID ${row.id}`}>
                      {reRequestingId === row.id ? '처리 중...' : '재요청'}
                    </button>
                  )}
                </td>
              </tr>
            );
          })
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
                  {detailData.requestReason != null && String(detailData.requestReason).trim() !== '' && (
                    <div className="search-history-detail-row">
                      <span className="search-history-detail-key">요청 사유</span>
                      <span className="search-history-detail-value">{detailData.requestReason}</span>
                    </div>
                  )}
                  <SearchParamsDetailView searchParams={detailData.searchParams} />
                  <SearchHistoryDetailCounts detailData={detailData} />
                  <DecryptionRequestedSection detailData={detailData} />
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {conditionsModalOpen && (
        <div
          className="search-history-detail-modal"
          onClick={handleConditionsOverlayClick}
          role="dialog"
          aria-modal="true"
          aria-labelledby="search-history-conditions-title"
        >
          <div className="search-history-detail-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="search-history-detail-header">
              <h3 id="search-history-conditions-title">검색 조건</h3>
              <button
                type="button"
                className="search-history-detail-close"
                onClick={closeConditionsModal}
                aria-label="닫기"
              >
                닫기
              </button>
            </div>
            <div className="search-history-detail-content">
              {conditionsModalLoading && <p>불러오는 중...</p>}
              {conditionsModalError && <div className="search-history-error">{conditionsModalError}</div>}
              {!conditionsModalLoading && !conditionsModalError && conditionsModalData && (
                <SearchParamsDetailView searchParams={conditionsModalData.searchParams} />
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
