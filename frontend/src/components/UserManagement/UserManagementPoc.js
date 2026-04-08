import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button } from '@mui/material';
import {
  DEFAULT_EMPLOYEES_PAGE_SIZE,
  EMPLOYEES_PAGE_SIZE_OPTIONS,
  MAX_EMPLOYEES_PAGE_SIZE,
  MIN_EMPLOYEES_PAGE_SIZE,
} from '../../config/hrSyncPocUi';
import { fetchSnapshots } from '../../services/hrSyncPocService';
import {
  fetchReplicaDepartmentTree,
  fetchReplicaUsers,
  isPocUserMgmtDisabled,
  isPocUserMgmtUnauthorized,
  postMigratePreview,
} from '../../services/pocUserManagementService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import '../UserPermissionHierarchy/UserPermissionHierarchy.css';
import './UserManagement.css';
import './UserManagementPoc.css';

const SOURCE_SYSTEM_DEFAULT = 'HR_SAMPLE';

/** @param {string|null|undefined} name */
const departmentUiLabel = (name) => {
  const t = name != null ? String(name).trim() : '';
  return t || '부서명 미등록';
};

function collectExpandableKeys(nodes, into = new Set()) {
  (nodes || []).forEach((n) => {
    const ch = n.children || [];
    if (ch.length > 0) {
      const k = n.departmentKey != null ? String(n.departmentKey) : '';
      if (k) into.add(k);
    }
    collectExpandableKeys(ch, into);
  });
  return into;
}

/**
 * @param {Array<{ departmentKey?: string, name?: string, children?: unknown[] }>} nodes
 * @param {Set<string>} expandedKeys
 * @param {(key: string) => void} onToggle
 * @param {(key: string) => void} onSelectDepartment
 * @param {string|null} selectedKey
 * @param {number} level
 * @param {boolean} isRoot
 */
function ReplicaDeptTree({
  nodes,
  expandedKeys,
  onToggle,
  onSelectDepartment,
  selectedKey,
  level = 0,
  isRoot = false,
}) {
  if (!nodes || nodes.length === 0) return null;
  return (
    <ul
      className="dept-tree-list"
      role={isRoot ? 'tree' : 'group'}
      aria-label={isRoot ? '복제 부서 트리 (PoC)' : undefined}
    >
      {nodes.map((node, idx) => {
        const key = node.departmentKey != null ? String(node.departmentKey) : `__row-${level}-${idx}`;
        const children = node.children || [];
        const hasChildren = children.length > 0;
        const isExpanded = expandedKeys.has(key);
        return (
          <li
            key={key}
            className="dept-tree-item"
            role="treeitem"
            aria-expanded={hasChildren ? isExpanded : undefined}
            aria-selected={selectedKey === key}
            style={{ paddingLeft: `${level * 1.25}rem` }}
          >
            <div className="dept-tree-node-wrapper">
              {hasChildren ? (
                <button
                  type="button"
                  className="dept-tree-toggle"
                  onClick={() => onToggle(key)}
                  aria-expanded={isExpanded}
                  aria-label={isExpanded ? '접기' : '펼치기'}
                  title={isExpanded ? '접기' : '펼치기'}
                >
                  <span className="dept-tree-chevron" aria-hidden>
                    {isExpanded ? '▼' : '▶'}
                  </span>
                </button>
              ) : (
                <span className="dept-tree-toggle-placeholder" aria-hidden />
              )}
              <button
                type="button"
                className={`dept-tree-label dept-tree-label-button ${selectedKey === key ? 'selected' : ''}`}
                onClick={() => onSelectDepartment(key)}
                aria-pressed={selectedKey === key}
              >
                {departmentUiLabel(node.name)}
              </button>
            </div>
            {hasChildren && isExpanded && (
              <ReplicaDeptTree
                nodes={children}
                expandedKeys={expandedKeys}
                onToggle={onToggle}
                onSelectDepartment={onSelectDepartment}
                selectedKey={selectedKey}
                level={level + 1}
                isRoot={false}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Read-only PoC clone of UM v2 — replica tree + replica users; no production user-management-v2 API.
 * Props: `user` accepted for parity with `UserManagement` (App passes session); this screen uses PoC APIs only.
 */
function UserManagementPoc() {
  const [treeLoading, setTreeLoading] = useState(true);
  const [treeError, setTreeError] = useState(null);
  const [roots, setRoots] = useState([]);
  const [expandedKeys, setExpandedKeys] = useState(() => new Set());

  const [snapshotsLoading, setSnapshotsLoading] = useState(false);
  const [snapshotsError, setSnapshotsError] = useState(null);
  const [snapshots, setSnapshots] = useState([]);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState('');

  const [selectedDepartmentKey, setSelectedDepartmentKey] = useState(null);

  const [usersLoading, setUsersLoading] = useState(false);
  const [usersError, setUsersError] = useState(null);
  const [userRows, setUserRows] = useState([]);
  const [pagination, setPagination] = useState(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_EMPLOYEES_PAGE_SIZE);

  const [migrateLoading, setMigrateLoading] = useState(false);
  const [migrateFeedback, setMigrateFeedback] = useState(null);

  const loadTree = useCallback(async () => {
    setTreeLoading(true);
    setTreeError(null);
    try {
      const res = await fetchReplicaDepartmentTree(SOURCE_SYSTEM_DEFAULT);
      const data = res?.data;
      const nextRoots = Array.isArray(data?.roots) ? data.roots : [];
      setRoots(nextRoots);
    } catch (err) {
      logger.debug('PoC UM replica tree failed', { code: err.code, status: err.status });
      if (isPocUserMgmtUnauthorized(err)) {
        setTreeError(getErrorMessage(err, '로그인이 필요합니다.'));
      } else if (isPocUserMgmtDisabled(err)) {
        setTreeError('HR Sync PoC가 비활성화되어 있습니다 (POC_DISABLED).');
      } else {
        setTreeError(getErrorMessage(err, '복제 부서 트리를 불러오지 못했습니다.'));
      }
      setRoots([]);
    } finally {
      setTreeLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTree();
  }, [loadTree]);

  useEffect(() => {
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
        if (err?.code === 'POC_DISABLED') {
          setSnapshots([]);
          setSnapshotsError('HR Sync PoC가 비활성화되어 있습니다.');
          return;
        }
        setSnapshotsError(getErrorMessage(err, '스냅샷 목록을 불러오지 못했습니다.'));
        setSnapshots([]);
      } finally {
        if (!cancelled) setSnapshotsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setPage(1);
  }, [selectedDepartmentKey, selectedSnapshotId]);

  useEffect(() => {
    if (!selectedDepartmentKey) {
      setUserRows([]);
      setPagination(null);
      setUsersError(null);
      setUsersLoading(false);
      return undefined;
    }
    let cancelled = false;
    (async () => {
      setUsersLoading(true);
      setUsersError(null);
      try {
        const res = await fetchReplicaUsers({
          sourceSystem: SOURCE_SYSTEM_DEFAULT,
          snapshotId: selectedSnapshotId || undefined,
          departmentKey: selectedDepartmentKey,
          page,
          size: pageSize,
        });
        if (cancelled) return;
        const data = res?.data;
        const rows = Array.isArray(data?.users)
          ? data.users
          : Array.isArray(data?.employees)
            ? data.employees
            : [];
        setUserRows(rows);
        const pag = data?.pagination;
        setPagination(
          pag
            ? {
                currentPage: pag.currentPage ?? page,
                totalPages: Math.max(1, pag.totalPages ?? 1),
                totalCount: pag.totalCount ?? 0,
              }
            : {
                currentPage: page,
                totalPages: 1,
                totalCount: rows.length,
              },
        );
      } catch (err) {
        if (cancelled) return;
        if (isPocUserMgmtUnauthorized(err)) {
          setUsersError(getErrorMessage(err, '로그인이 필요합니다.'));
        } else if (isPocUserMgmtDisabled(err)) {
          setUsersError('HR Sync PoC가 비활성화되어 있습니다.');
        } else {
          setUsersError(getErrorMessage(err, '복제 사용자 목록을 불러오지 못했습니다.'));
        }
        setUserRows([]);
        setPagination(null);
      } finally {
        if (!cancelled) setUsersLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedDepartmentKey, selectedSnapshotId, page, pageSize]);

  const handleToggle = useCallback((key) => {
    setExpandedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  const handleExpandAll = useCallback(() => {
    setExpandedKeys(collectExpandableKeys(roots));
  }, [roots]);

  const handleCollapseAll = useCallback(() => {
    setExpandedKeys(new Set());
  }, []);

  const handleSelectDepartment = useCallback((key) => {
    setSelectedDepartmentKey(key);
  }, []);

  const handleMigratePreview = useCallback(async () => {
    setMigrateFeedback(null);
    setMigrateLoading(true);
    try {
      const res = await postMigratePreview();
      const data = res?.data;
      const messageCode = data?.messageCode != null ? String(data.messageCode) : '';
      const persisted = data?.persisted === true;
      const msg = `마이그레이션 미리보기(스텁): messageCode=${messageCode || '—'}, persisted=${persisted}`;
      setMigrateFeedback({ type: 'success', text: msg });
      window.alert(msg);
    } catch (err) {
      const text = getErrorMessage(err, '마이그레이션 미리보기 요청에 실패했습니다.');
      setMigrateFeedback({ type: 'error', text });
      window.alert(text);
    } finally {
      setMigrateLoading(false);
    }
  }, []);

  const onPageSizeChange = (next) => {
    const n = Math.min(MAX_EMPLOYEES_PAGE_SIZE, Math.max(MIN_EMPLOYEES_PAGE_SIZE, next));
    setPageSize(n);
    setPage(1);
  };

  const selectedDeptLabel = useMemo(() => {
    if (!selectedDepartmentKey) return '미선택';
    const findName = (nodes) => {
      for (const n of nodes || []) {
        const k = n.departmentKey != null ? String(n.departmentKey) : '';
        if (k === selectedDepartmentKey) return departmentUiLabel(n.name);
        const hit = findName(n.children || []);
        if (hit) return hit;
      }
      return null;
    };
    return findName(roots) || selectedDepartmentKey;
  }, [roots, selectedDepartmentKey]);

  const treeDisabled = treeLoading || !!treeError;

  return (
    <div className="user-management" data-testid="user-management-poc-root">
      <div className="user-management-header">
        <h2>사용자 관리 v2 (PoC)</h2>
      </div>
      <p className="user-permission-hierarchy-hint">
        HR 복제 데이터 조회 전용 화면입니다. 프로덕션 사용자/부서 변경 API를 호출하지 않습니다.
      </p>
      <p className="user-management-poc-readonly-note" role="note">
        부서 생성·수정·삭제, 직접 사용자 등록, 삭제 기능은 제공하지 않습니다.
      </p>
      {migrateFeedback && (
        <p
          className={migrateFeedback.type === 'error' ? 'user-management-error' : 'user-management-v2-success'}
          role="status"
        >
          {migrateFeedback.text}
        </p>
      )}
      {treeError && (
        <div className="user-management-error" role="alert">
          {treeError}
        </div>
      )}

      <div className="user-permission-hierarchy-layout">
        <section className="user-permission-hierarchy-tree-section" aria-label="복제 부서 트리">
          <div
            className="user-management-v2-tree-bulk-actions"
            role="group"
            aria-label="부서 트리 일괄 펼침·접기"
          >
            <Button
              type="button"
              size="small"
              variant="outlined"
              className="user-management-v2-tree-bulk-btn"
              disabled={treeDisabled || roots.length === 0}
              onClick={handleExpandAll}
            >
              모두 펼치기
            </Button>
            <Button
              type="button"
              size="small"
              variant="outlined"
              className="user-management-v2-tree-bulk-btn"
              disabled={treeDisabled || roots.length === 0}
              onClick={handleCollapseAll}
            >
              모두 접기
            </Button>
          </div>
          <div className="user-management-v2-tree-toolbar">
            <p className="user-management-v2-selected-dept">
              선택 부서:
              {' '}
              {selectedDeptLabel}
            </p>
          </div>
          {treeLoading ? (
            <p aria-live="polite">목록을 불러오는 중…</p>
          ) : roots.length === 0 && !treeError ? (
            <p className="user-management-v2-tree-empty">복제 부서가 없습니다.</p>
          ) : (
            <ReplicaDeptTree
              nodes={roots}
              expandedKeys={expandedKeys}
              onToggle={handleToggle}
              onSelectDepartment={handleSelectDepartment}
              selectedKey={selectedDepartmentKey}
              isRoot
            />
          )}
        </section>

        <section className="user-management-poc-detail-section" aria-label="복제 사용자 목록">
          <div className="user-management-poc-toolbar">
            <label htmlFor="um-poc-snapshot-select">
              스냅샷 (선택)
              <select
                id="um-poc-snapshot-select"
                className="form-control"
                value={selectedSnapshotId}
                onChange={(e) => setSelectedSnapshotId(e.target.value)}
                disabled={snapshotsLoading || !!snapshotsError}
                aria-label="PoC 스냅샷 필터"
              >
                <option value="">전체·미지정</option>
                {snapshots.map((s) => (
                  <option key={s.snapshotId} value={s.snapshotId}>
                    {s.label ? `${s.label} (${s.snapshotId})` : s.snapshotId}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="user-management-btn-primary"
              onClick={handleMigratePreview}
              disabled={migrateLoading}
            >
              {migrateLoading ? '처리 중…' : '마이그레이션 미리보기 (PoC)'}
            </button>
          </div>
          {snapshotsLoading && (
            <p className="hr-sync-poc-status" role="status">
              스냅샷 목록을 불러오는 중…
            </p>
          )}
          {snapshotsError && (
            <p className="user-management-error" role="alert">
              {snapshotsError}
            </p>
          )}
          {!selectedDepartmentKey ? (
            <p role="status">왼쪽 트리에서 부서를 선택하면 복제 사용자 목록이 표시됩니다.</p>
          ) : usersLoading ? (
            <p aria-live="polite">사용자 목록을 불러오는 중…</p>
          ) : usersError ? (
            <p className="user-management-error" role="alert">
              {usersError}
            </p>
          ) : (
            <>
              <div className="log-table-container">
                <div className="table-wrapper">
                  <table className="user-management-table" aria-label="복제 사용자 표">
                    <thead>
                      <tr>
                        <th scope="col">표시명</th>
                        <th scope="col">직책</th>
                        <th scope="col">부서 키</th>
                        <th scope="col">부서명</th>
                        <th scope="col">활성</th>
                        <th scope="col">사번</th>
                      </tr>
                    </thead>
                    <tbody>
                      {userRows.length === 0 ? (
                        <tr>
                          <td colSpan={6}>표시할 복제 사용자가 없습니다.</td>
                        </tr>
                      ) : (
                        userRows.map((row) => {
                          const active =
                            row.active === true ||
                            row.isActive === true ||
                            row.active === 'true' ||
                            row.isActive === 'true';
                          const key =
                            row.externalEmployeeId != null
                              ? String(row.externalEmployeeId)
                              : `${row.employeeNumber ?? ''}-${row.displayName ?? ''}`;
                          return (
                            <tr key={key}>
                              <td>{row.displayName ?? '—'}</td>
                              <td>{row.jobTitle ?? '—'}</td>
                              <td>{row.departmentKey ?? '—'}</td>
                              <td>{row.departmentName ?? '—'}</td>
                              <td>{active ? '예' : '아니오'}</td>
                              <td>{row.employeeNumber ?? '—'}</td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
              {pagination && (
                <div className="hr-sync-poc-pagination" style={{ marginTop: '0.75rem' }}>
                  <button
                    type="button"
                    className="user-management-btn"
                    disabled={page <= 1 || usersLoading}
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                  >
                    이전
                  </button>
                  <span style={{ margin: '0 0.5rem' }}>
                    {pagination.currentPage} / {pagination.totalPages} (총 {pagination.totalCount}건)
                  </span>
                  <button
                    type="button"
                    className="user-management-btn"
                    disabled={page >= pagination.totalPages || usersLoading}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    다음
                  </button>
                  <label htmlFor="um-poc-page-size" style={{ marginLeft: '1rem' }}>
                    페이지 크기
                    <select
                      id="um-poc-page-size"
                      value={pageSize}
                      onChange={(e) => onPageSizeChange(Number(e.target.value))}
                      disabled={usersLoading}
                    >
                      {EMPLOYEES_PAGE_SIZE_OPTIONS.map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}

export default UserManagementPoc;
