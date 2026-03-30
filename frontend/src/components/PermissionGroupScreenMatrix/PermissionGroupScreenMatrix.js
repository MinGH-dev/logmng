import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Tooltip, Snackbar, Alert } from '@mui/material';
import {
  listPermissionGroups,
  getPermissionGroup,
  updatePermissionGroup,
  createPermissionGroup,
  deletePermissionGroup,
} from '../../services/permissionGroupService';
import { getAllowedScreenIds, getScreenFunctions } from '../../utils/security';
import {
  ACTION_DISABLED_TOOLTIPS,
  SCREENS_WITH_WRITE,
  SCREENS_WITH_APPROVE,
  SCREENS_WITH_DECRYPT,
  FUNCTION_LABELS,
  APPROVE_CHECKBOX_TOOLTIP,
} from '../../constants/screenFunctionDescriptions';
import { getErrorMessage } from '../../utils/errorMessage';
import { MENU_TREE } from '../../constants/menuTree';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import '../PermissionGroupManagement/PermissionGroupManagement.css';
import './PermissionGroupScreenMatrix.css';
import {
  normalizeAllowedScreens,
  toAllowedScreensPayload,
  flattenMenuTreeToRows,
  createAllowedEntryForScreen,
  SCOPE_OPTIONS,
  APPROVAL_SCOPE_FIXED_SCREENS,
  SCOPE_SUPPORTING_SCREENS,
} from './allowedScreensMatrixUtils';

const MATRIX_TITLE = '권한 그룹 관리 v2.0.0';

const unwrapGroupDetail = (apiResult) => {
  if (!apiResult || typeof apiResult !== 'object') return null;
  const d = apiResult.data;
  if (d && typeof d === 'object' && d.id != null) return d;
  if (apiResult.id != null) return apiResult;
  return null;
};

const getItemForScreen = (normalized, screenId) =>
  normalized.find((s) => s.screenId === screenId);

const PermissionGroupScreenMatrix = ({ user }) => {
  const ids = getAllowedScreenIds(user);
  const screenFunctions = getScreenFunctions(user);
  const canAccess =
    user?.isSystemAdmin === true ||
    (Array.isArray(ids) &&
      (ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy')));
  const canWrite =
    screenFunctions?.['permission-group-management']?.write === true ||
    screenFunctions?.['user-permission-hierarchy']?.write === true;

  const [groups, setGroups] = useState([]);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [listError, setListError] = useState(null);

  const [selectedGroupId, setSelectedGroupId] = useState(null);
  const [groupDetail, setGroupDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);

  const [allowedScreens, setAllowedScreens] = useState([]);
  const [saveReasonOpen, setSaveReasonOpen] = useState(false);
  const [saveReason, setSaveReason] = useState('');
  const [saveReasonError, setSaveReasonError] = useState(null);
  const [saving, setSaving] = useState(false);

  const [snackbar, setSnackbar] = useState({
    open: false,
    message: '',
    severity: 'success',
  });

  const [createOpen, setCreateOpen] = useState(false);
  const [createCode, setCreateCode] = useState('');
  const [createName, setCreateName] = useState('');
  const [createError, setCreateError] = useState(null);
  const [createSubmitting, setCreateSubmitting] = useState(false);

  const [deleteSubmitting, setDeleteSubmitting] = useState(false);

  const [sortConfig, setSortConfig] = useState({ key: 'order', direction: 'asc' });

  const flatRows = useMemo(() => flattenMenuTreeToRows(MENU_TREE), []);

  const sortedRows = useMemo(() => {
    const rows = [...flatRows];
    const { key, direction } = sortConfig;
    const dir = direction === 'asc' ? 1 : -1;
    if (key === 'order') {
      rows.sort((a, b) => dir * (a.order - b.order));
      return rows;
    }
    if (key === 'group') {
      rows.sort((a, b) => dir * String(a.groupLabel).localeCompare(String(b.groupLabel), 'ko'));
      return rows;
    }
    if (key === 'screen') {
      rows.sort((a, b) => dir * String(a.screenLabel).localeCompare(String(b.screenLabel), 'ko'));
      return rows;
    }
    return rows;
  }, [flatRows, sortConfig]);

  const normalizedScreens = useMemo(
    () => normalizeAllowedScreens(allowedScreens),
    [allowedScreens]
  );

  const loadGroups = useCallback(async () => {
    if (!canAccess) return;
    setGroupsLoading(true);
    setListError(null);
    try {
      const data = await listPermissionGroups();
      const list = Array.isArray(data) ? data : [];
      list.sort((a, b) => String(a.code || '').localeCompare(String(b.code || ''), 'ko'));
      setGroups(list);
    } catch (e) {
      logger.error('권한 그룹 목록 조회 실패:', e);
      setListError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '목록을 불러오지 못했습니다.'));
      setGroups([]);
    } finally {
      setGroupsLoading(false);
    }
  }, [canAccess]);

  useEffect(() => {
    loadGroups();
  }, [loadGroups]);

  const loadGroupDetail = useCallback(async (id) => {
    if (id == null) return;
    setDetailLoading(true);
    setDetailError(null);
    setGroupDetail(null);
    setAllowedScreens([]);
    try {
      const res = await getPermissionGroup(id);
      const detail = unwrapGroupDetail(res);
      if (!detail) {
        setDetailError('그룹 정보를 불러오지 못했습니다.');
        return;
      }
      setGroupDetail(detail);
      setAllowedScreens(normalizeAllowedScreens(detail.allowedScreens));
    } catch (e) {
      logger.error('권한 그룹 상세 조회 실패:', e);
      setDetailError(getErrorMessage(e, '상세를 불러오지 못했습니다.'));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedGroupId != null) {
      loadGroupDetail(selectedGroupId);
    } else {
      setGroupDetail(null);
      setAllowedScreens([]);
      setDetailError(null);
    }
  }, [selectedGroupId, loadGroupDetail]);

  const handleSelectGroup = (id) => {
    setSelectedGroupId(id);
  };

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const sortAria = (key) => {
    if (sortConfig.key !== key) return 'none';
    return sortConfig.direction === 'asc' ? 'ascending' : 'descending';
  };

  const toggleScreenEnabled = (screenId, enabled) => {
    setAllowedScreens((prev) => {
      const norm = normalizeAllowedScreens(prev);
      const exists = norm.some((s) => s.screenId === screenId);
      if (enabled && !exists) {
        return normalizeAllowedScreens([...norm, createAllowedEntryForScreen(screenId)]);
      }
      if (!enabled && exists) {
        return norm.filter((s) => s.screenId !== screenId);
      }
      return norm;
    });
  };

  const changeScope = (screenId, scope) => {
    setAllowedScreens((prev) => {
      const norm = normalizeAllowedScreens(prev);
      return normalizeAllowedScreens(
        norm.map((s) => (s.screenId === screenId ? { ...s, scope } : s))
      );
    });
  };

  const changeWrite = (screenId, checked) => {
    setAllowedScreens((prev) => {
      const norm = normalizeAllowedScreens(prev);
      return normalizeAllowedScreens(
        norm.map((s) => (s.screenId === screenId ? { ...s, write: checked } : s))
      );
    });
  };

  const changeApprove = (screenId, checked) => {
    setAllowedScreens((prev) => {
      const norm = normalizeAllowedScreens(prev);
      return normalizeAllowedScreens(
        norm.map((s) => {
          if (s.screenId !== screenId) return s;
          const updated = { ...s, approve: checked };
          if (checked === true && APPROVAL_SCOPE_FIXED_SCREENS.includes(screenId)) {
            updated.scope = 'team';
          }
          return updated;
        })
      );
    });
  };

  const changeDecrypt = (screenId, checked) => {
    setAllowedScreens((prev) => {
      const norm = normalizeAllowedScreens(prev);
      return normalizeAllowedScreens(
        norm.map((s) => (s.screenId === screenId ? { ...s, decrypt: checked } : s))
      );
    });
  };

  const handleSaveClick = () => {
    if (!groupDetail?.id || !canWrite) return;
    setSaveReason('');
    setSaveReasonError(null);
    setSaveReasonOpen(true);
  };

  const handleSaveReasonCancel = useCallback(() => {
    setSaveReasonOpen(false);
    setSaveReason('');
    setSaveReasonError(null);
  }, []);

  const handleSaveReasonConfirm = async () => {
    const reason = (saveReason || '').trim();
    if (!reason) {
      setSaveReasonError('저장 사유를 입력하세요.');
      return;
    }
    if (!groupDetail?.id) return;
    setSaving(true);
    setSaveReasonError(null);
    try {
      const payload = toAllowedScreensPayload(normalizeAllowedScreens(allowedScreens));
      await updatePermissionGroup(groupDetail.id, {
        code: groupDetail.code,
        name: groupDetail.name,
        description: groupDetail.description ?? null,
        allowedScreens: payload,
        changeReason: reason,
      });
      setSaveReasonOpen(false);
      setSaveReason('');
      await loadGroups();
      await loadGroupDetail(groupDetail.id);
      setSnackbar({ open: true, message: '저장되었습니다.', severity: 'success' });
    } catch (err) {
      logger.error('권한 그룹 수정 실패:', err);
      setSaveReasonError(getErrorMessage(err, '수정에 실패했습니다.'));
    } finally {
      setSaving(false);
    }
  };

  useEffect(() => {
    if (!saveReasonOpen) return;
    const onKey = (e) => {
      if (e.key === 'Escape') handleSaveReasonCancel();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [saveReasonOpen, handleSaveReasonCancel]);

  const closeCreateDialog = useCallback(() => {
    if (createSubmitting) return;
    setCreateOpen(false);
    setCreateCode('');
    setCreateName('');
    setCreateError(null);
  }, [createSubmitting]);

  useEffect(() => {
    if (!createOpen) return;
    const onKey = (e) => {
      if (e.key === 'Escape') closeCreateDialog();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [createOpen, closeCreateDialog]);

  const openCreateDialog = () => {
    if (!canWrite) return;
    setCreateCode('');
    setCreateName('');
    setCreateError(null);
    setCreateOpen(true);
  };

  const handleCreateConfirm = async () => {
    const code = (createCode || '').trim();
    const name = (createName || '').trim();
    if (!code || !name) {
      setCreateError('코드와 이름을 입력하세요.');
      return;
    }
    setCreateSubmitting(true);
    setCreateError(null);
    try {
      const res = await createPermissionGroup({
        code,
        name,
        description: null,
        allowedScreens: [],
      });
      const created = unwrapGroupDetail(res) ?? (res?.data?.id != null ? res.data : null);
      const newId = created?.id;
      setCreateOpen(false);
      setCreateCode('');
      setCreateName('');
      await loadGroups();
      if (newId != null) setSelectedGroupId(newId);
      setSnackbar({ open: true, message: '권한 그룹이 추가되었습니다.', severity: 'success' });
    } catch (e) {
      logger.error('권한 그룹 생성 실패:', e);
      setCreateError(getErrorMessage(e, '추가에 실패했습니다.'));
    } finally {
      setCreateSubmitting(false);
    }
  };

  const handleDeleteSelected = async () => {
    if (!canWrite || selectedGroupId == null || deleteSubmitting) return;
    if (!window.confirm('선택한 권한 그룹을 삭제할까요?')) return;
    const id = selectedGroupId;
    setDeleteSubmitting(true);
    try {
      await deletePermissionGroup(id);
      setSelectedGroupId(null);
      await loadGroups();
      setSnackbar({ open: true, message: '삭제되었습니다.', severity: 'success' });
    } catch (e) {
      logger.error('권한 그룹 삭제 실패:', e);
      setSnackbar({
        open: true,
        message: getErrorMessage(e, '삭제에 실패했습니다.'),
        severity: 'error',
      });
    } finally {
      setDeleteSubmitting(false);
    }
  };

  if (!canAccess) {
    return (
      <div className="pgsm-root">
        <h2 className="pgsm-title">{MATRIX_TITLE}</h2>
        <p className="user-management-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  const matrixDisabled = !canWrite || !groupDetail;
  const readOnlyHint = !canWrite ? '읽기 전용입니다. 수정 권한이 있는 계정으로 로그인하세요.' : null;

  return (
    <div
      className="pgsm-root wf-layout-main"
      data-pgsm-view="permission-group-screen-matrix"
      data-layout="two-pane-group-list-matrix"
      data-matrix-pagination="none"
      data-group-list-actions="add-delete"
      data-scope-cell="compact-2char"
      data-grid-checkbox-size="14"
    >
      {/*
        Layout intent: assets/svg/scenes/req-20260323-02-permission-group-matrix-final.svg (#wf-main-layout-req02)
        Root SVG data-* mirrored above for traceability (two-pane list + matrix, no matrix pagination,
        group list header add/delete, compact scope cell ~52×22 / short labels, 14px grid checkboxes).
      */}
      <h2 className="pgsm-title">{MATRIX_TITLE}</h2>
      <p className="pgsm-hint">
        권한 그룹을 선택한 뒤 화면별 접근·조회 범위·기능을 설정합니다. 저장 시 사유가 필요합니다.
      </p>
      {readOnlyHint && (
        <p className="pgsm-readonly-banner" role="status">
          {readOnlyHint}
        </p>
      )}
      {listError && (
        <div className="user-management-error" role="alert">
          {listError}
        </div>
      )}

      <div className="pgsm-layout">
        <aside className="pgsm-sidebar" aria-label="권한 그룹 목록">
          <div className="pgsm-sidebar-header">
            <h3 className="pgsm-sidebar-heading" id="pgsm-group-list-title">
              권한 그룹 목록
            </h3>
            <div className="pgsm-sidebar-header-actions">
              <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.write : ''}>
                <span>
                  <button
                    type="button"
                    className="pgsm-list-header-btn"
                    id="btn-group-add"
                    onClick={openCreateDialog}
                    disabled={!canWrite || groupsLoading || createSubmitting}
                    aria-disabled={!canWrite || groupsLoading || createSubmitting}
                  >
                    추가
                  </button>
                </span>
              </Tooltip>
              <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.write : ''}>
                <span>
                  <button
                    type="button"
                    className="pgsm-list-header-btn"
                    id="btn-group-delete"
                    onClick={handleDeleteSelected}
                    disabled={
                      !canWrite ||
                      selectedGroupId == null ||
                      groupsLoading ||
                      deleteSubmitting
                    }
                    aria-disabled={
                      !canWrite ||
                      selectedGroupId == null ||
                      groupsLoading ||
                      deleteSubmitting
                    }
                  >
                    삭제
                  </button>
                </span>
              </Tooltip>
            </div>
          </div>
          <div className="pgsm-group-list" aria-labelledby="pgsm-group-list-title">
            {groupsLoading ? (
              <p className="pgsm-muted">목록을 불러오는 중…</p>
            ) : groups.length === 0 ? (
              <p className="pgsm-muted">등록된 권한 그룹이 없습니다.</p>
            ) : (
              groups.map((g) => (
                <button
                  key={g.id}
                  type="button"
                  className={`pgsm-group-item${selectedGroupId === g.id ? ' is-selected' : ''}`}
                  onClick={() => handleSelectGroup(g.id)}
                  aria-pressed={selectedGroupId === g.id}
                  aria-label={`${g.name}, ${g.code}`}
                >
                  <span className="pgsm-group-code">{g.code}</span>
                  <span className="pgsm-group-name">{g.name}</span>
                </button>
              ))
            )}
          </div>
        </aside>

        <section className="pgsm-main" aria-label="화면별 권한 매트릭스">
          {!selectedGroupId && (
            <p className="pgsm-placeholder">왼쪽에서 권한 그룹을 선택하세요.</p>
          )}
          {selectedGroupId && detailLoading && (
            <p className="pgsm-muted">상세를 불러오는 중…</p>
          )}
          {detailError && (
            <div className="user-management-error" role="alert">
              {detailError}
            </div>
          )}

          {selectedGroupId && groupDetail && !detailLoading && (
            <>
              <div className="pgsm-matrix-toolbar">
                <div className="pgsm-selected-summary">
                  <strong>{groupDetail.name}</strong>
                  <span className="pgsm-meta">({groupDetail.code})</span>
                </div>
                <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.write : ''}>
                  <span>
                    <button
                      type="button"
                      className="user-management-btn add"
                      onClick={handleSaveClick}
                      disabled={matrixDisabled || saving}
                      aria-disabled={matrixDisabled || saving}
                    >
                      {saving ? '처리 중...' : '저장'}
                    </button>
                  </span>
                </Tooltip>
              </div>

              <div className="log-table-container pgsm-table-container">
                <div className="table-wrapper pgsm-table-wrapper">
                  <table className="log-table pgsm-matrix-table" aria-label="화면별 권한">
                    {/*
                      Column widths vs req-20260323-02 SVG vertical guides (498…1142, total 644):
                      메뉴 56, 화면명 216, 메뉴·API 52, 조회 범위 62, 수정 64, 승인 108, 복호화 86.
                    */}
                    <colgroup>
                      <col className="pgsm-col-menu" style={{ width: '8.7%' }} />
                      <col className="pgsm-col-screen" style={{ width: '33.54%' }} />
                      <col className="pgsm-col-menu-api" style={{ width: '8.07%' }} />
                      <col className="pgsm-col-scope" style={{ width: '9.63%' }} />
                      <col className="pgsm-col-write" style={{ width: '9.94%' }} />
                      <col className="pgsm-col-approve" style={{ width: '16.77%' }} />
                      <col className="pgsm-col-decrypt" style={{ width: '13.35%' }} />
                    </colgroup>
                    <thead>
                      <tr>
                        <th
                          scope="col"
                          className="pgsm-sortable"
                          aria-sort={sortAria('group')}
                        >
                          <button
                            type="button"
                            className="pgsm-sort-btn"
                            onClick={() => handleSort('group')}
                          >
                            메뉴 구분
                          </button>
                        </th>
                        <th
                          scope="col"
                          className="pgsm-sortable"
                          aria-sort={sortAria('screen')}
                        >
                          <button
                            type="button"
                            className="pgsm-sort-btn"
                            onClick={() => handleSort('screen')}
                          >
                            화면명
                          </button>
                        </th>
                        <th scope="col" className="pgsm-th-cb">
                          메뉴·API 사용
                        </th>
                        <th scope="col">조회 범위</th>
                        <th scope="col" className="pgsm-th-cb">
                          {FUNCTION_LABELS.write}
                        </th>
                        <th scope="col" className="pgsm-th-cb">
                          승인 권한
                        </th>
                        <th scope="col" className="pgsm-th-cb">
                          복호화 허용
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {sortedRows.map((row) => {
                        const item = getItemForScreen(normalizedScreens, row.screenId);
                        const enabled = !!item;
                        const supportsScope = SCOPE_SUPPORTING_SCREENS.includes(row.screenId);
                        const supportsWrite = SCREENS_WITH_WRITE.includes(row.screenId);
                        const supportsApprove = SCREENS_WITH_APPROVE.includes(row.screenId);
                        const supportsDecrypt = SCREENS_WITH_DECRYPT.includes(row.screenId);
                        const scopeFixed =
                          supportsScope &&
                          supportsApprove &&
                          (item?.approve ?? false) === true;
                        const scopeValue = scopeFixed
                          ? 'team'
                          : item?.scope ?? (supportsScope ? 'team' : 'self');
                        const writeChecked = item?.write ?? true;
                        const approveChecked = item?.approve ?? false;
                        const decryptChecked = item?.decrypt ?? false;
                        const disabledRow = matrixDisabled || !enabled;
                        const scopeSelectId = `pgsm-scope-${row.childId}`;
                        const screenToggleId = `pgsm-screen-${row.childId}`;

                        return (
                          <tr key={row.childId}>
                            <td>{row.groupLabel}</td>
                            <td>{row.screenLabel}</td>
                            <td className="pgsm-td-cb">
                              <input
                                id={screenToggleId}
                                type="checkbox"
                                className="pgsm-checkbox"
                                checked={enabled}
                                onChange={(e) => toggleScreenEnabled(row.screenId, e.target.checked)}
                                disabled={matrixDisabled}
                                aria-label={`${row.screenLabel} 메뉴·API 사용`}
                              />
                            </td>
                            <td className="pgsm-td-scope">
                              {!enabled || !supportsScope ? (
                                <span className="pgsm-cell-dash">—</span>
                              ) : scopeFixed ? (
                                <select
                                  className="grid-select-field pgsm-scope-grid-select is-locked"
                                  value="team"
                                  disabled
                                  aria-readonly="true"
                                  aria-label={`${row.screenLabel} 조회 범위 (승인 시 부서 고정)`}
                                >
                                  <option value="team">부서</option>
                                </select>
                              ) : (
                                <select
                                  id={scopeSelectId}
                                  className="grid-select-field pgsm-scope-grid-select"
                                  value={scopeValue}
                                  onChange={(e) => changeScope(row.screenId, e.target.value)}
                                  disabled={disabledRow}
                                  aria-label={`${row.screenLabel} 조회 범위`}
                                >
                                  {SCOPE_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>
                                      {opt.label}
                                    </option>
                                  ))}
                                </select>
                              )}
                            </td>
                            <td className="pgsm-td-cb">
                              {!enabled || !supportsWrite ? (
                                <span className="pgsm-cell-dash">—</span>
                              ) : (
                                <input
                                  type="checkbox"
                                  className="pgsm-checkbox"
                                  checked={writeChecked}
                                  onChange={(e) => changeWrite(row.screenId, e.target.checked)}
                                  disabled={disabledRow}
                                  aria-label={`${row.screenLabel} ${FUNCTION_LABELS.write}`}
                                />
                              )}
                            </td>
                            <td className="pgsm-td-cb">
                              {!enabled || !supportsApprove ? (
                                <span className="pgsm-cell-dash">—</span>
                              ) : (
                                <Tooltip title={APPROVE_CHECKBOX_TOOLTIP} arrow placement="top">
                                  <span className="pgsm-checkbox-tooltip-anchor">
                                    <input
                                      type="checkbox"
                                      className="pgsm-checkbox"
                                      checked={approveChecked}
                                      onChange={(e) => changeApprove(row.screenId, e.target.checked)}
                                      disabled={disabledRow}
                                      aria-label={`${row.screenLabel} ${FUNCTION_LABELS.approve}`}
                                    />
                                  </span>
                                </Tooltip>
                              )}
                            </td>
                            <td className="pgsm-td-cb">
                              {!enabled || !supportsDecrypt ? (
                                <span className="pgsm-cell-dash">—</span>
                              ) : (
                                <input
                                  type="checkbox"
                                  className="pgsm-checkbox"
                                  checked={decryptChecked}
                                  onChange={(e) => changeDecrypt(row.screenId, e.target.checked)}
                                  disabled={disabledRow}
                                  aria-label={`${row.screenLabel} ${FUNCTION_LABELS.decrypt}`}
                                />
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          )}
        </section>
      </div>

      {createOpen && (
        <div
          className="permission-group-dialog-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="pgsm-create-title"
        >
          <div className="permission-group-dialog">
            <h3 id="pgsm-create-title">권한 그룹 추가</h3>
            <p className="permission-group-hint">코드와 이름을 입력하세요. 화면 권한은 목록에서 그룹을 연 뒤 설정합니다.</p>
            {createError && (
              <div className="user-management-error" role="alert">
                {createError}
              </div>
            )}
            <div className="permission-group-form-row">
              <label htmlFor="pgsm-create-code">
                코드 <span aria-hidden>*</span>
              </label>
              <input
                id="pgsm-create-code"
                type="text"
                value={createCode}
                onChange={(ev) => setCreateCode(ev.target.value)}
                maxLength={128}
                autoComplete="off"
                autoFocus
                disabled={createSubmitting}
              />
            </div>
            <div className="permission-group-form-row">
              <label htmlFor="pgsm-create-name">
                이름 <span aria-hidden>*</span>
              </label>
              <input
                id="pgsm-create-name"
                type="text"
                value={createName}
                onChange={(ev) => setCreateName(ev.target.value)}
                maxLength={256}
                autoComplete="off"
                disabled={createSubmitting}
              />
            </div>
            <div className="permission-group-dialog-actions">
              <button
                type="button"
                className="user-management-btn add"
                onClick={handleCreateConfirm}
                disabled={createSubmitting}
              >
                {createSubmitting ? '처리 중...' : '확인'}
              </button>
              <button
                type="button"
                className="user-management-btn"
                onClick={closeCreateDialog}
                disabled={createSubmitting}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      {saveReasonOpen && (
        <div
          className="permission-group-dialog-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="pgsm-save-reason-title"
        >
          <div className="permission-group-dialog">
            <h3 id="pgsm-save-reason-title">저장 사유</h3>
            <p className="permission-group-hint">
              권한 그룹 변경 내용을 저장합니다. 사유를 입력한 뒤 확인을 누르세요.
            </p>
            {saveReasonError && (
              <div className="user-management-error" role="alert">
                {saveReasonError}
              </div>
            )}
            <div className="permission-group-form-row">
              <label htmlFor="pgsm-save-reason">
                사유 <span aria-hidden>*</span>
              </label>
              <textarea
                id="pgsm-save-reason"
                value={saveReason}
                onChange={(ev) => setSaveReason(ev.target.value)}
                rows={4}
                maxLength={2000}
                required
                aria-required="true"
                autoComplete="off"
                autoFocus
                disabled={saving}
              />
            </div>
            <div className="permission-group-dialog-actions">
              <button
                type="button"
                className="user-management-btn add"
                onClick={handleSaveReasonConfirm}
                disabled={saving}
              >
                {saving ? '처리 중...' : '확인'}
              </button>
              <button
                type="button"
                className="user-management-btn"
                onClick={handleSaveReasonCancel}
                disabled={saving}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
          severity={snackbar.severity === 'error' ? 'error' : 'success'}
          variant="filled"
          sx={{ width: '100%' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </div>
  );
};

export default PermissionGroupScreenMatrix;
