/**
 * Permission group list, CRUD dialogs, and user assign/remove.
 * Used as the right panel of UserPermissionHierarchy (single-screen permission management).
 * Optionally rendered standalone by PermissionGroupManagement for backward compatibility.
 */
import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Tooltip } from '@mui/material';
import {
  listPermissionGroups,
  createPermissionGroup,
  updatePermissionGroup,
  deletePermissionGroup,
  listUsersInGroup,
  addUserToGroup,
  removeUserFromGroup,
} from '../../services/permissionGroupService';
import { getUsers } from '../../services/userService';
import { getAllowedScreenIds, getScreenFunctions } from '../../utils/security';
import {
  ACTION_DISABLED_TOOLTIPS,
  SCREENS_WITH_WRITE,
  SCREENS_WITH_APPROVE,
} from '../../constants/screenFunctionDescriptions';
import { getErrorMessage } from '../../utils/errorMessage';
import DataTable, { EmptyTableBody } from '../DataTable';
import ScreenSelectionTree from './ScreenSelectionTree';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import './PermissionGroupManagement.css';

const GROUP_COLUMNS = [
  { key: 'code', label: '코드', sortable: true },
  { key: 'name', label: '이름', sortable: true },
  { key: 'description', label: '설명', sortable: false },
  { key: 'actions', label: '동작', sortable: false },
];

const PermissionGroupPanel = ({ user, onRefreshHierarchy }) => {
  const [groups, setGroups] = useState([]);
  const [userList, setUserList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionId, setActionId] = useState(null);
  const [sortConfig, setSortConfig] = useState({ key: 'code', direction: 'asc' });

  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editGroup, setEditGroup] = useState(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [groupToDelete, setGroupToDelete] = useState(null);
  const [usersDialogOpen, setUsersDialogOpen] = useState(false);
  const [usersDialogGroup, setUsersDialogGroup] = useState(null);
  const [usersInGroup, setUsersInGroup] = useState([]);
  const [usersDialogLoading, setUsersDialogLoading] = useState(false);
  const [addUserId, setAddUserId] = useState('');
  const [usersDialogError, setUsersDialogError] = useState(null);
  const [usersDialogActionId, setUsersDialogActionId] = useState(null);
  const [createDialogError, setCreateDialogError] = useState(null);
  const [editDialogError, setEditDialogError] = useState(null);
  const [deleteDialogError, setDeleteDialogError] = useState(null);
  const [createAllowedScreens, setCreateAllowedScreens] = useState([]);
  const [editAllowedScreens, setEditAllowedScreens] = useState([]);

  const ids = getAllowedScreenIds(user);
  const screenFunctions = getScreenFunctions(user);
  const canAccessPermissionGroupManagement =
    user?.isSystemAdmin === true ||
    (Array.isArray(ids) &&
      (ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy')));
  const canWrite =
    screenFunctions?.['permission-group-management']?.write === true ||
    screenFunctions?.['user-permission-hierarchy']?.write === true;

  const sortedGroups = useMemo(() => {
    if (!groups.length || !sortConfig.key) return groups;
    const key = sortConfig.key;
    const dir = sortConfig.direction === 'asc' ? 1 : -1;
    return [...groups].sort((a, b) => {
      const va = a[key] ?? '';
      const vb = b[key] ?? '';
      return dir * String(va).localeCompare(String(vb));
    });
  }, [groups, sortConfig.key, sortConfig.direction]);

  const loadGroups = useCallback(async () => {
    if (!canAccessPermissionGroupManagement) return;
    setLoading(true);
    setError(null);
    try {
      const data = await listPermissionGroups();
      setGroups(Array.isArray(data) ? data : []);
    } catch (e) {
      logger.error('권한 그룹 목록 조회 실패:', e);
      setError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '목록을 불러오지 못했습니다.'));
      setGroups([]);
    } finally {
      setLoading(false);
    }
  }, [canAccessPermissionGroupManagement]);

  const loadUsers = useCallback(async () => {
    if (!canAccessPermissionGroupManagement) return;
    try {
      const result = await getUsers();
      const data = result.data;
      setUserList(Array.isArray(data) ? data : (data?.data || []));
    } catch (e) {
      logger.error('사용자 목록 조회 실패:', e);
      setUserList([]);
    }
  }, [canAccessPermissionGroupManagement]);

  useEffect(() => {
    loadGroups();
    loadUsers();
  }, [loadGroups, loadUsers]);

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  /** Screens where approval scope is fixed to team. req 20260306-approval-scope-fixed-department */
  const APPROVAL_SCOPE_FIXED_SCREENS = ['search-history', 'pending-approvals'];

  /** Legacy screen ID: normalize to canonical (req 20260318-permission-group-menu-invalid-screen-id-imagelog). */
  const normalizeScreenId = (id) => (id === 'java-fw_imagelog' ? 'java-fw-imagelog' : id);

  /** Normalize allowedScreens to [{ screenId, scope?, read?, write?, approve?, decrypt? }]. API may return string[] or object array.
   * Preserves explicit false for write/approve/decrypt when API returns partial data. When approve=true for approval-fixed screens, scope is set to 'team'. req 20250303, 20260306, 20260306-search-screen-decrypt-permission */
  const normalizeAllowedScreens = (arr) => {
    const scopeScreens = ['activity-log', 'statistics', 'search-history', 'pending-approvals'];
    const decryptScreens = ['pb-feplog', 'java-fw-imagelog'];
    if (!Array.isArray(arr)) return [];
    return arr.map((s) => {
      const rawId = typeof s === 'string' ? s : s.screenId;
      const screenId = normalizeScreenId(rawId);
      const base = typeof s === 'string'
        ? { screenId, scope: scopeScreens.includes(screenId) ? 'self' : undefined }
        : {
            screenId,
            scope: s.scope || (scopeScreens.includes(screenId) ? 'self' : undefined),
            read: s.read,
            write: s.write,
            approve: s.approve,
            decrypt: s.decrypt,
          };
      const hasWrite = SCREENS_WITH_WRITE.includes(base.screenId);
      const hasApprove = SCREENS_WITH_APPROVE.includes(base.screenId);
      const hasDecrypt = decryptScreens.includes(base.screenId);
      const approved = base.approve ?? (hasApprove ? false : undefined);
      const decryptVal = base.decrypt ?? (hasDecrypt ? false : undefined);
      let scope = base.scope ?? (scopeScreens.includes(base.screenId) ? 'self' : undefined);
      if (approved === true && APPROVAL_SCOPE_FIXED_SCREENS.includes(base.screenId)) {
        scope = 'team';
      }
      return {
        ...base,
        scope,
        read: base.read ?? true,
        write: base.write ?? (hasWrite ? true : undefined),
        approve: approved ?? (hasApprove ? false : undefined),
        decrypt: decryptVal ?? (hasDecrypt ? false : undefined),
      };
    });
  };

  /** Build API payload for allowedScreens. Sends screenId, scope (when applicable), read, write, approve, decrypt per spec §1.1. For approval-fixed screens with approve=true, scope is always 'team'. req 20260306, 20260306-search-screen-decrypt-permission */
  const toAllowedScreensPayload = (screens) =>
    screens.map((s) => {
      const item = { screenId: s.screenId };
      let scope = s.scope;
      if (s.approve === true && APPROVAL_SCOPE_FIXED_SCREENS.includes(s.screenId)) {
        scope = 'team';
      }
      if (scope) item.scope = scope;
      if (s.read !== undefined) item.read = s.read;
      if (s.write !== undefined) item.write = s.write;
      if (s.approve !== undefined) item.approve = s.approve;
      if (s.decrypt !== undefined) item.decrypt = s.decrypt;
      return item;
    });

  const openEdit = (group) => {
    setEditGroup(group);
    setEditAllowedScreens(normalizeAllowedScreens(group?.allowedScreens));
    setEditOpen(true);
    setError(null);
    setEditDialogError(null);
  };

  const openDelete = (group) => {
    setGroupToDelete(group);
    setDeleteOpen(true);
    setError(null);
    setDeleteDialogError(null);
  };

  const openUsersDialog = async (group) => {
    setUsersDialogGroup(group);
    setUsersDialogOpen(true);
    setAddUserId('');
    setUsersDialogError(null);
    setUsersDialogLoading(true);
    try {
      const list = await listUsersInGroup(group.id);
      setUsersInGroup(Array.isArray(list) ? list : []);
    } catch (e) {
      logger.error('그룹 사용자 목록 조회 실패:', e);
      setUsersDialogError(getErrorMessage(e, '사용자 목록을 불러오지 못했습니다.'));
      setUsersInGroup([]);
    } finally {
      setUsersDialogLoading(false);
    }
  };

  const notifyHierarchyRefresh = useCallback(() => {
    if (typeof onRefreshHierarchy === 'function') onRefreshHierarchy();
  }, [onRefreshHierarchy]);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    const form = e.target;
    const code = (form.code && form.code.value && form.code.value.trim()) || '';
    const name = (form.name && form.name.value && form.name.value.trim()) || '';
    const description = (form.description && form.description.value && form.description.value.trim()) || null;
    if (!code || !name) {
      setCreateDialogError('코드와 이름을 입력하세요.');
      return;
    }
    setActionId('create');
    setCreateDialogError(null);
    try {
      const allowedScreens = toAllowedScreensPayload(createAllowedScreens);
      await createPermissionGroup({ code, name, description, allowedScreens });
      setCreateOpen(false);
      setCreateAllowedScreens([]);
      form.reset();
      await loadGroups();
      notifyHierarchyRefresh();
    } catch (e) {
      logger.error('권한 그룹 생성 실패:', e);
      setCreateDialogError(getErrorMessage(e, '추가에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!editGroup || !editGroup.id) return;
    const form = e.target;
    const code = (form.code && form.code.value && form.code.value.trim()) || '';
    const name = (form.name && form.name.value && form.name.value.trim()) || '';
    const description = (form.description && form.description.value && form.description.value.trim()) || null;
    if (!code || !name) {
      setEditDialogError('코드와 이름을 입력하세요.');
      return;
    }
    setActionId('edit');
    setEditDialogError(null);
    try {
      const allowedScreens = toAllowedScreensPayload(editAllowedScreens);
      await updatePermissionGroup(editGroup.id, { code, name, description, allowedScreens });
      setEditOpen(false);
      setEditGroup(null);
      await loadGroups();
      notifyHierarchyRefresh();
    } catch (e) {
      logger.error('권한 그룹 수정 실패:', e);
      setEditDialogError(getErrorMessage(e, '수정에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!groupToDelete || !groupToDelete.id) return;
    setActionId('delete');
    setDeleteDialogError(null);
    try {
      await deletePermissionGroup(groupToDelete.id);
      setDeleteOpen(false);
      setGroupToDelete(null);
      await loadGroups();
      notifyHierarchyRefresh();
    } catch (e) {
      logger.error('권한 그룹 삭제 실패:', e);
      setDeleteDialogError(getErrorMessage(e, '삭제에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleAddUserToGroup = async () => {
    if (!usersDialogGroup || !addUserId?.trim()) return;
    const userIdNum = Number(addUserId.trim());
    if (Number.isNaN(userIdNum)) return;
    setUsersDialogActionId(String(userIdNum));
    setUsersDialogError(null);
    try {
      await addUserToGroup(usersDialogGroup.id, userIdNum);
      setAddUserId('');
      const list = await listUsersInGroup(usersDialogGroup.id);
      setUsersInGroup(Array.isArray(list) ? list : []);
      notifyHierarchyRefresh();
    } catch (e) {
      logger.error('사용자 배정 실패:', e);
      setUsersDialogError(getErrorMessage(e, '사용자 배정에 실패했습니다.'));
    } finally {
      setUsersDialogActionId(null);
    }
  };

  const handleRemoveUserFromGroup = async (userId) => {
    if (!usersDialogGroup) return;
    setUsersDialogActionId(String(userId));
    setUsersDialogError(null);
    try {
      await removeUserFromGroup(usersDialogGroup.id, userId);
      const list = await listUsersInGroup(usersDialogGroup.id);
      setUsersInGroup(Array.isArray(list) ? list : []);
      notifyHierarchyRefresh();
    } catch (e) {
      logger.error('사용자 제거 실패:', e);
      setUsersDialogError(getErrorMessage(e, '사용자 제거에 실패했습니다.'));
    } finally {
      setUsersDialogActionId(null);
    }
  };

  useEffect(() => {
    if (!usersDialogOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        setUsersDialogOpen(false);
        setUsersDialogGroup(null);
        setUsersInGroup([]);
        setUsersDialogError(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [usersDialogOpen]);

  const closeUsersDialog = useCallback(() => {
    setUsersDialogOpen(false);
    setUsersDialogGroup(null);
    setUsersInGroup([]);
    setUsersDialogError(null);
  }, []);

  const alreadyInGroup = (userId) => usersInGroup.some((u) => String(u.userId ?? u.username) === String(userId));
  const addableUsers = userList.filter((u) => {
    const id = u.userId ?? u.username;
    return id != null && id !== '' && !alreadyInGroup(id);
  });

  return (
    <>
      {error && (
        <div className="user-management-error" role="alert">
          {error}
        </div>
      )}
      <div className="permission-group-actions">
        <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.create : ''}>
          <span>
            <button
              type="button"
              className="user-management-btn add"
              onClick={() => { setCreateOpen(true); setError(null); setCreateDialogError(null); }}
              disabled={!canWrite}
              aria-disabled={!canWrite}
              aria-label="권한 그룹 추가"
            >
              권한 그룹 추가
            </button>
          </span>
        </Tooltip>
      </div>
      <DataTable
        columns={GROUP_COLUMNS}
        sortConfig={sortConfig}
        onSort={handleSort}
        loading={loading}
        emptyMessage="등록된 권한 그룹이 없습니다."
        emptyColSpan={4}
        ariaLabel="권한 그룹 목록"
      >
        {sortedGroups.length === 0 ? (
          <EmptyTableBody colSpan={4} message="등록된 권한 그룹이 없습니다." />
        ) : (
          sortedGroups.map((row) => (
            <tr key={row.id}>
              <td>{row.code}</td>
              <td>{row.name}</td>
              <td>{row.description ?? '-'}</td>
              <td>
                <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.edit : ''}>
                  <span>
                    <button
                      type="button"
                      className="user-management-btn add"
                      onClick={() => openEdit(row)}
                      disabled={!canWrite}
                      aria-disabled={!canWrite}
                      aria-label={`수정, ${row.code}`}
                    >
                      수정
                    </button>
                  </span>
                </Tooltip>
                <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.delete : ''}>
                  <span>
                    <button
                      type="button"
                      className="user-management-btn remove"
                      onClick={() => openDelete(row)}
                      disabled={!canWrite}
                      aria-disabled={!canWrite}
                      aria-label={`삭제, ${row.code}`}
                    >
                      삭제
                    </button>
                  </span>
                </Tooltip>
                <Tooltip title={!canWrite ? ACTION_DISABLED_TOOLTIPS.write : ''}>
                  <span>
                    <button
                      type="button"
                      className="user-management-btn add"
                      onClick={() => openUsersDialog(row)}
                      disabled={!canWrite}
                      aria-disabled={!canWrite}
                      aria-label={`사용자 관리, ${row.code}`}
                    >
                      사용자 관리
                    </button>
                  </span>
                </Tooltip>
              </td>
            </tr>
          ))
        )}
      </DataTable>

      {createOpen && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-create-title">
          <div className="permission-group-dialog">
            <h3 id="dialog-create-title">권한 그룹 추가</h3>
            {createDialogError && (
              <div className="user-management-error" role="alert">{createDialogError}</div>
            )}
            <form onSubmit={handleCreateSubmit}>
              <div className="permission-group-form-row">
                <label htmlFor="create-code">코드 <span aria-hidden>*</span></label>
                <input id="create-code" name="code" type="text" required autoComplete="off" aria-required="true" />
              </div>
              <div className="permission-group-form-row">
                <label htmlFor="create-name">이름 <span aria-hidden>*</span></label>
                <input id="create-name" name="name" type="text" required autoComplete="off" aria-required="true" />
              </div>
              <div className="permission-group-form-row">
                <label htmlFor="create-description">설명</label>
                <input id="create-description" name="description" type="text" autoComplete="off" />
              </div>
              <div className="permission-group-form-row">
                <span className="permission-group-form-label">접근 화면</span>
                <ScreenSelectionTree
                  selectedScreens={createAllowedScreens}
                  onChange={setCreateAllowedScreens}
                />
              </div>
              <div className="permission-group-dialog-actions">
                <button type="submit" className="user-management-btn add" disabled={!!actionId}>{(actionId === 'create') ? '처리 중...' : '추가'}</button>
                <button type="button" className="user-management-btn" onClick={() => { setCreateOpen(false); setError(null); setCreateDialogError(null); }}>취소</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editOpen && editGroup && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-edit-title">
          <div className="permission-group-dialog">
            <h3 id="dialog-edit-title">권한 그룹 수정</h3>
            {editDialogError && (
              <div className="user-management-error" role="alert">{editDialogError}</div>
            )}
            <form onSubmit={handleEditSubmit}>
              <div className="permission-group-form-row">
                <label htmlFor="edit-code">코드 <span aria-hidden>*</span></label>
                <input id="edit-code" name="code" type="text" defaultValue={editGroup.code} required autoComplete="off" aria-required="true" />
              </div>
              <div className="permission-group-form-row">
                <label htmlFor="edit-name">이름 <span aria-hidden>*</span></label>
                <input id="edit-name" name="name" type="text" defaultValue={editGroup.name} required autoComplete="off" aria-required="true" />
              </div>
              <div className="permission-group-form-row">
                <label htmlFor="edit-description">설명</label>
                <input id="edit-description" name="description" type="text" defaultValue={editGroup.description ?? ''} autoComplete="off" />
              </div>
              <div className="permission-group-form-row">
                <span className="permission-group-form-label">접근 화면</span>
                <ScreenSelectionTree
                  selectedScreens={editAllowedScreens}
                  onChange={(next) => setEditAllowedScreens(next)}
                />
              </div>
              <div className="permission-group-dialog-actions">
                <button type="submit" className="user-management-btn add" disabled={!!actionId}>{(actionId === 'edit') ? '처리 중...' : '저장'}</button>
                <button type="button" className="user-management-btn" onClick={() => { setEditOpen(false); setEditGroup(null); setError(null); setEditDialogError(null); }}>취소</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deleteOpen && groupToDelete && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-delete-title">
          <div className="permission-group-dialog">
            <h3 id="dialog-delete-title">권한 그룹 삭제</h3>
            {deleteDialogError && (
              <div className="user-management-error" role="alert">{deleteDialogError}</div>
            )}
            <p>삭제하시겠습니까? 사용자가 할당되어 있으면 삭제할 수 없습니다. &quot;{groupToDelete.name}&quot; ({groupToDelete.code})</p>
            <div className="permission-group-dialog-actions">
              <button type="button" className="user-management-btn remove" onClick={handleDeleteConfirm} disabled={!!actionId} aria-label="삭제 확인">
                {(actionId === 'delete') ? '처리 중...' : '삭제'}
              </button>
              <button type="button" className="user-management-btn" onClick={() => { setDeleteOpen(false); setGroupToDelete(null); setError(null); setDeleteDialogError(null); }}>취소</button>
            </div>
          </div>
        </div>
      )}

      {usersDialogOpen && usersDialogGroup && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-users-title">
          <div className="permission-group-dialog permission-group-dialog-wide permission-group-dialog-users">
            <div className="permission-group-dialog-header">
              <h3 id="dialog-users-title">사용자 할당 — {usersDialogGroup.name} ({usersDialogGroup.code})</h3>
              <button
                type="button"
                className="permission-group-dialog-close"
                onClick={closeUsersDialog}
                aria-label="닫기"
              >
                ×
              </button>
            </div>
            <div className="permission-group-dialog-body">
              {usersDialogError && <div className="user-management-error" role="alert">{usersDialogError}</div>}
              <div className="permission-group-user-add">
                <select
                  value={addUserId}
                  onChange={(e) => setAddUserId(e.target.value)}
                  aria-label="추가할 사용자 선택"
                >
                  <option value="">— 사용자 선택 —</option>
                  {addableUsers.map((u) => {
                    const id = u.userId ?? u.username;
                    return (
                      <option key={id} value={String(id)}>
                        {id} {u.departmentCode ? `(${u.departmentCode})` : ''}
                      </option>
                    );
                  })}
                </select>
                <button
                  type="button"
                  className="user-management-btn add"
                  onClick={handleAddUserToGroup}
                  disabled={!addUserId || !!usersDialogActionId}
                  aria-label="사용자 추가"
                >
                  {usersDialogActionId ? '처리 중...' : '추가'}
                </button>
              </div>
              {usersDialogLoading ? (
                <p>목록을 불러오는 중…</p>
              ) : (
                <div className="log-table-container">
                  <div className="table-wrapper">
                    <table className="log-table" aria-label="이 그룹에 배정된 사용자">
                      <thead>
                        <tr>
                          <th scope="col">사용자 ID</th>
                          <th scope="col">동작</th>
                        </tr>
                      </thead>
                      <tbody>
                        {usersInGroup.length === 0 ? (
                          <tr><td colSpan={2} className="no-data">배정된 사용자가 없습니다.</td></tr>
                        ) : (
                          usersInGroup.map((u) => {
                            const uid = u.userId ?? u.username;
                            return (
                              <tr key={uid}>
                                <td>{uid}</td>
                                <td>
                                  <button type="button" className="user-management-btn remove" onClick={() => handleRemoveUserFromGroup(uid)} disabled={usersDialogActionId === String(uid)} aria-label={`제거, ${uid}`}>
                                    {usersDialogActionId === String(uid) ? '처리 중...' : '제거'}
                                  </button>
                                </td>
                              </tr>
                            );
                          })
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
            <div className="permission-group-dialog-actions permission-group-dialog-footer">
              <button type="button" className="user-management-btn" onClick={closeUsersDialog} aria-label="닫기">닫기</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default PermissionGroupPanel;
