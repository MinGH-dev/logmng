import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  DEFAULT_EMPLOYEES_PAGE_SIZE,
  EMPLOYEES_PAGE_SIZE_OPTIONS,
  MAX_EMPLOYEES_PAGE_SIZE,
  MIN_EMPLOYEES_PAGE_SIZE,
} from '../../config/hrSyncPocUi';
import DataTable, { EmptyTableBody } from '../DataTable';
import { useSortConfig } from '../../hooks/useSortConfig';
import {
  fetchEmployees,
  fetchSnapshots,
  getHrSyncPocConfig,
  postHrSyncPocPreview,
} from '../../services/hrSyncPocService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import './HrSyncPocPreview.css';

const POC_DISABLED_MESSAGE = 'PoC disabled';

/** Spec classification keys — show all rows so zeros are visible (TC-06). */
const PREVIEW_CLASSIFICATION_KEYS = [
  'TRANSFER',
  'NEW_HIRE',
  'RESIGNED',
  'UNCHANGED',
  'PROFILE_UPDATE_NON_SECURITY',
  'CONFLICT',
  'ORPHAN',
];

const PREVIEW_CLASSIFICATION_LABELS = {
  TRANSFER: '전환(이동)',
  NEW_HIRE: '신규 입사',
  RESIGNED: '퇴사',
  UNCHANGED: '변경 없음',
  PROFILE_UPDATE_NON_SECURITY: '프로필 변경(비보안)',
  CONFLICT: '충돌',
  ORPHAN: '고아(미매칭)',
};

function formatPreviewScalar(value) {
  if (value === null || value === undefined || value === '') return '—';
  return String(value);
}

function previewFailureMessage(res) {
  const fromApi = res?.error != null ? String(res.error).trim() : '';
  if (fromApi) return fromApi;
  return getErrorMessage({ code: res?.code, message: res?.error }, '미리보기에 실패했습니다.');
}

function isPocDisabledError(err) {
  return err?.code === 'POC_DISABLED';
}

const EMP_COLS = [
  { key: 'displayName', label: '표시명', sortable: true },
  { key: 'jobTitle', label: '직책', sortable: true },
  { key: 'departmentKey', label: '부서 키', sortable: true },
  { key: 'departmentName', label: '부서명', sortable: true },
  { key: 'active', label: '활성', sortable: true },
  { key: 'employeeNumber', label: '사원번호', sortable: true },
];

function compareEmployeeRows(a, b, key, direction) {
  const mul = direction === 'asc' ? 1 : -1;
  if (key === 'active') {
    const an = a.active === true ? 1 : 0;
    const bn = b.active === true ? 1 : 0;
    return (an - bn) * mul;
  }
  const av = a[key];
  const bv = b[key];
  const as = av == null ? '' : String(av);
  const bs = bv == null ? '' : String(bv);
  return as.localeCompare(bs, 'ko', { numeric: true }) * mul;
}

/**
 * Read-only HR Sync PoC preview UI (no apply). specs/hr-sync-poc.spec.yaml
 */
function HrSyncPocPreview() {
  const [configLoading, setConfigLoading] = useState(true);
  const [configError, setConfigError] = useState(null);
  const [pocEnabledFromConfig, setPocEnabledFromConfig] = useState(null);

  const [snapshotsLoading, setSnapshotsLoading] = useState(false);
  const [snapshotsError, setSnapshotsError] = useState(null);
  const [snapshots, setSnapshots] = useState([]);

  const [selectedSnapshotId, setSelectedSnapshotId] = useState('');
  const [empPage, setEmpPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_EMPLOYEES_PAGE_SIZE);

  const [employeesLoading, setEmployeesLoading] = useState(false);
  const [employeesError, setEmployeesError] = useState(null);
  const [employeesRaw, setEmployeesRaw] = useState([]);
  const [empPagination, setEmpPagination] = useState(null);

  const [sortConfig, handleSort] = useSortConfig('displayName', 'asc');

  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState(null);
  const [previewSummary, setPreviewSummary] = useState(null);
  /** Last API body for optional collapsed JSON (success or structured failure). */
  const [previewRawPayload, setPreviewRawPayload] = useState(null);

  const sortedEmployees = useMemo(() => {
    const list = Array.isArray(employeesRaw) ? [...employeesRaw] : [];
    if (!sortConfig.key) return list;
    list.sort((a, b) => compareEmployeeRows(a, b, sortConfig.key, sortConfig.direction));
    return list;
  }, [employeesRaw, sortConfig.key, sortConfig.direction]);

  const loadConfig = useCallback(async () => {
    setConfigLoading(true);
    setConfigError(null);
    setPocEnabledFromConfig(null);
    try {
      const res = await getHrSyncPocConfig();
      const enabled = res?.data?.pocEnabled === true;
      setPocEnabledFromConfig(enabled);
    } catch (err) {
      logger.debug('HR Sync PoC config failed', { code: err.code, status: err.status });
      if (isPocDisabledError(err)) {
        setPocEnabledFromConfig(false);
      } else {
        setConfigError(getErrorMessage(err));
      }
    } finally {
      setConfigLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  useEffect(() => {
    if (pocEnabledFromConfig !== true) return undefined;
    let cancelled = false;
    (async () => {
      setSnapshotsLoading(true);
      setSnapshotsError(null);
      try {
        const res = await fetchSnapshots();
        if (cancelled) return;
        setSnapshots(res?.data?.snapshots ?? []);
      } catch (err) {
        if (cancelled) return;
        if (isPocDisabledError(err)) {
          setPocEnabledFromConfig(false);
          return;
        }
        setSnapshotsError(getErrorMessage(err));
        setSnapshots([]);
      } finally {
        if (!cancelled) setSnapshotsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [pocEnabledFromConfig]);

  useEffect(() => {
    setEmpPage(1);
  }, [selectedSnapshotId]);

  useEffect(() => {
    if (pocEnabledFromConfig !== true || !selectedSnapshotId) {
      setEmployeesRaw([]);
      setEmpPagination(null);
      setEmployeesError(null);
      setEmployeesLoading(false);
      return undefined;
    }
    let cancelled = false;
    (async () => {
      setEmployeesLoading(true);
      setEmployeesError(null);
      try {
        const res = await fetchEmployees(selectedSnapshotId, empPage, pageSize);
        if (cancelled) return;
        const data = res?.data;
        setEmployeesRaw(data?.employees ?? []);
        const pag = data?.pagination;
        setEmpPagination(
          pag
            ? {
                currentPage: pag.currentPage ?? empPage,
                totalPages: Math.max(1, pag.totalPages ?? 1),
                totalCount: pag.totalCount ?? 0,
              }
            : {
                currentPage: empPage,
                totalPages: 1,
                totalCount: (data?.employees ?? []).length,
              },
        );
      } catch (err) {
        if (cancelled) return;
        if (isPocDisabledError(err)) {
          setPocEnabledFromConfig(false);
          return;
        }
        setEmployeesError(getErrorMessage(err));
        setEmployeesRaw([]);
        setEmpPagination(null);
      } finally {
        if (!cancelled) setEmployeesLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [pocEnabledFromConfig, selectedSnapshotId, empPage, pageSize]);

  const runPreview = async () => {
    setPreviewError(null);
    setPreviewSummary(null);
    setPreviewRawPayload(null);
    setPreviewLoading(true);
    try {
      const body = {};
      if (selectedSnapshotId.trim() !== '') body.snapshotId = selectedSnapshotId.trim();
      const res = await postHrSyncPocPreview(body);
      setPreviewRawPayload(res ?? null);
      if (res && res.success === false) {
        setPreviewError(previewFailureMessage(res));
        return;
      }
      const data = res?.data;
      if (!data) {
        setPreviewError('미리보기 응답에 데이터가 없습니다.');
        return;
      }
      const { classificationCounts, previewId, snapshotId: snap, riskTier, upstreamGateStatus, messageCode } = data;
      setPreviewSummary({
        previewId,
        snapshotId: snap,
        classificationCounts: classificationCounts ?? {},
        riskTier,
        upstreamGateStatus,
        messageCode,
      });
    } catch (err) {
      if (isPocDisabledError(err)) {
        setPocEnabledFromConfig(false);
      }
      setPreviewRawPayload(err?.payload ?? null);
      setPreviewError(getErrorMessage(err));
    } finally {
      setPreviewLoading(false);
    }
  };

  const onPageSizeChange = (next) => {
    const n = Math.min(MAX_EMPLOYEES_PAGE_SIZE, Math.max(MIN_EMPLOYEES_PAGE_SIZE, next));
    setPageSize(n);
    setEmpPage(1);
  };

  if (configLoading) {
    return (
      <div className="user-management hr-sync-poc-preview" role="status" aria-live="polite">
        <h2>HR Sync PoC (preview)</h2>
        <p>설정을 불러오는 중…</p>
      </div>
    );
  }

  if (configError) {
    return (
      <div className="user-management hr-sync-poc-preview">
        <h2>HR Sync PoC (preview)</h2>
        <p className="user-management-error" role="alert">
          {configError}
        </p>
      </div>
    );
  }

  if (pocEnabledFromConfig !== true) {
    return (
      <div className="user-management hr-sync-poc-preview">
        <h2>HR Sync PoC (preview)</h2>
        <p className="hr-sync-poc-disabled">{POC_DISABLED_MESSAGE}</p>
      </div>
    );
  }

  return (
    <div className="user-management hr-sync-poc-preview">
      <h2>HR Sync PoC (preview)</h2>
      <p className="user-management-hint hr-sync-poc-hint">
        읽기 전용 분류 요약·인력 조회입니다. 적용(apply) API는 호출하지 않습니다.
      </p>

      {snapshotsLoading && (
        <p className="hr-sync-poc-status" role="status" aria-live="polite">
          스냅샷 목록을 불러오는 중…
        </p>
      )}
      {snapshotsError && (
        <p className="hr-sync-poc-error" role="alert">
          {snapshotsError}
        </p>
      )}

      {!snapshotsLoading && !snapshotsError && snapshots.length === 0 && (
        <p className="hr-sync-poc-empty" role="status">
          등록된 PoC 스냅샷이 없습니다.
        </p>
      )}

      {!snapshotsLoading && !snapshotsError && (
        <>
          <div className="hr-sync-poc-form-row">
            {snapshots.length > 0 ? (
              <label htmlFor="hr-sync-poc-snapshot-select">
                스냅샷
                <select
                  id="hr-sync-poc-snapshot-select"
                  value={selectedSnapshotId}
                  onChange={(e) => setSelectedSnapshotId(e.target.value)}
                  disabled={previewLoading}
                  aria-label="PoC 스냅샷 선택"
                >
                  <option value="">스냅샷 선택…</option>
                  {snapshots.map((s) => (
                    <option key={s.snapshotId} value={s.snapshotId}>
                      {s.label ? `${s.label} (${s.snapshotId})` : s.snapshotId}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <button
              type="button"
              className="user-management-btn-primary"
              onClick={runPreview}
              disabled={previewLoading}
            >
              {previewLoading ? '실행 중…' : 'Run preview'}
            </button>
          </div>

          {(previewLoading || previewError || previewSummary !== null) && (
            <section
              className="hr-sync-poc-preview-summary"
              aria-labelledby="hr-sync-poc-summary-heading"
              data-testid="hr-sync-poc-preview-summary"
            >
              <h3 id="hr-sync-poc-summary-heading" className="hr-sync-poc-preview-summary-title">
                분류 요약
              </h3>
              {previewLoading && (
                <p className="hr-sync-poc-status" role="status" aria-live="polite">
                  미리보기 실행 중…
                </p>
              )}
              {!previewLoading && previewError && (
                <p className="hr-sync-poc-preview-summary-error" role="alert">
                  {previewError}
                </p>
              )}
              {!previewLoading && !previewError && previewSummary !== null && (
                <>
                  <table className="hr-sync-poc-preview-summary-table">
                    <caption className="hr-sync-poc-sr-only">분류별 건수</caption>
                    <thead>
                      <tr>
                        <th scope="col">분류</th>
                        <th scope="col">건수</th>
                      </tr>
                    </thead>
                    <tbody>
                      {PREVIEW_CLASSIFICATION_KEYS.map((key) => (
                        <tr key={key}>
                          <td>
                            {PREVIEW_CLASSIFICATION_LABELS[key] ?? key}
                            <span className="hr-sync-poc-preview-summary-code"> ({key})</span>
                          </td>
                          <td>{previewSummary.classificationCounts[key] ?? 0}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <dl className="hr-sync-poc-preview-summary-meta">
                    <div>
                      <dt>스냅샷 ID</dt>
                      <dd>{formatPreviewScalar(previewSummary.snapshotId)}</dd>
                    </div>
                    <div>
                      <dt>프리뷰 ID</dt>
                      <dd>{formatPreviewScalar(previewSummary.previewId)}</dd>
                    </div>
                    <div>
                      <dt>위험 등급 (riskTier)</dt>
                      <dd>{formatPreviewScalar(previewSummary.riskTier)}</dd>
                    </div>
                    <div>
                      <dt>업스트림 게이트 (upstreamGateStatus)</dt>
                      <dd>{formatPreviewScalar(previewSummary.upstreamGateStatus)}</dd>
                    </div>
                    <div>
                      <dt>메시지 코드 (messageCode)</dt>
                      <dd>{formatPreviewScalar(previewSummary.messageCode)}</dd>
                    </div>
                  </dl>
                </>
              )}
              {previewRawPayload != null && (
                <details className="hr-sync-poc-preview-raw-details">
                  <summary>응답 원문 (JSON)</summary>
                  <pre className="hr-sync-poc-json hr-sync-poc-json-collapsed">
                    {JSON.stringify(previewRawPayload, null, 2)}
                  </pre>
                </details>
              )}
            </section>
          )}

          {snapshots.length > 0 && !selectedSnapshotId && (
            <p className="hr-sync-poc-hint-muted" role="status">
              스냅샷을 선택하면 해당 배치의 인력(복제본) 첫 페이지가 표시됩니다.
            </p>
          )}

          {employeesError && (
            <p className="hr-sync-poc-error" role="alert">
              {employeesError}
            </p>
          )}

          {selectedSnapshotId ? (
            <DataTable
              ariaLabel="PoC 스냅샷 인력 목록"
              columns={EMP_COLS}
              sortConfig={sortConfig}
              onSort={handleSort}
              loading={employeesLoading}
              emptyMessage="인력 데이터가 없습니다."
              emptyColSpan={EMP_COLS.length}
              pageSize={pageSize}
              onPageSizeChange={onPageSizeChange}
              pageSizeOptions={EMPLOYEES_PAGE_SIZE_OPTIONS}
              pageSizeMin={MIN_EMPLOYEES_PAGE_SIZE}
              pageSizeMax={MAX_EMPLOYEES_PAGE_SIZE}
              pagination={
                empPagination
                  ? {
                      currentPage: empPagination.currentPage,
                      totalPages: empPagination.totalPages,
                      onPageChange: setEmpPage,
                      simple: true,
                      infoText: `총 ${empPagination.totalCount}건`,
                    }
                  : null
              }
              containerClassName="hr-sync-poc-emp-table"
            >
              {sortedEmployees.length === 0 && !employeesLoading ? (
                <EmptyTableBody colSpan={EMP_COLS.length} message="인력 데이터가 없습니다." />
              ) : (
                sortedEmployees.map((row, idx) => (
                  <tr key={`${row.displayName ?? ''}-${row.departmentKey ?? ''}-${idx}`}>
                    <td>{row.displayName ?? '—'}</td>
                    <td>{row.jobTitle ?? '—'}</td>
                    <td>{row.departmentKey ?? '—'}</td>
                    <td>{row.departmentName ?? '—'}</td>
                    <td>{row.active === true ? '예' : row.active === false ? '아니오' : '—'}</td>
                    <td>{row.employeeNumber ?? '—'}</td>
                  </tr>
                ))
              )}
            </DataTable>
          ) : null}
        </>
      )}
    </div>
  );
}

export default HrSyncPocPreview;
export { POC_DISABLED_MESSAGE };
