import React, { useState, useEffect, useMemo, useCallback } from 'react';
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
import { getErrorMessage } from '../../utils/errorMessage';
import DataTable, { EmptyTableBody } from '../DataTable';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import './PermissionGroupManagement.css';

const GROUP_COLUMNS = [
  { key: 'code', label: '코드', sortable: true },
  { key: 'name', label: '이름', sortable: true },
  { key: 'description', label: '설명', sortable: false },
  { key: 'actions', label: '동작', sortable: false },
];

const PermissionGroupManagement = ({ user }) => {
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

  const isAdmin = user?.role === 'ADMIN';

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
    if (!isAdmin) return;
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
  }, [isAdmin]);

  const loadUsers = useCallback(async () => {
    if (!isAdmin) return;
    try {
      const result = await getUsers();
      const data = result.data;
      setUserList(Array.isArray(data) ? data : (data?.data || []));
    } catch (e) {
      logger.error('사용자 목록 조회 실패:', e);
      setUserList([]);
    }
  }, [isAdmin]);

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

  const openEdit = (group) => {
    setEditGroup(group);
    setEditOpen(true);
    setError(null);
  };

  const openDelete = (group) => {
    setGroupToDelete(group);
    setDeleteOpen(true);
    setError(null);
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

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    const form = e.target;
    const code = (form.code && form.code.value && form.code.value.trim()) || '';
    const name = (form.name && form.name.value && form.name.value.trim()) || '';
    const description = (form.description && form.description.value && form.description.value.trim()) || null;
    if (!code || !name) {
      setError('코드와 이름을 입력하세요.');
      return;
    }
    setActionId('create');
    setError(null);
    try {
      await createPermissionGroup({ code, name, description });
      setCreateOpen(false);
      form.reset();
      await loadGroups();
    } catch (e) {
      logger.error('권한 그룹 생성 실패:', e);
      setError(getErrorMessage(e, '생성에 실패했습니다.'));
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
      setError('코드와 이름을 입력하세요.');
      return;
    }
    setActionId('edit');
    setError(null);
    try {
      await updatePermissionGroup(editGroup.id, { code, name, description });
      setEditOpen(false);
      setEditGroup(null);
      await loadGroups();
    } catch (e) {
      logger.error('권한 그룹 수정 실패:', e);
      setError(getErrorMessage(e, '수정에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!groupToDelete || !groupToDelete.id) return;
    setActionId('delete');
    setError(null);
    try {
      await deletePermissionGroup(groupToDelete.id);
      setDeleteOpen(false);
      setGroupToDelete(null);
      await loadGroups();
    } catch (e) {
      logger.error('권한 그룹 삭제 실패:', e);
      setError(getErrorMessage(e, '삭제에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleAddUserToGroup = async () => {
    if (!usersDialogGroup || !addUserId?.trim()) return;
    const userId = addUserId.trim();
    setUsersDialogActionId(userId);
    setUsersDialogError(null);
    try {
      await addUserToGroup(usersDialogGroup.id, userId);
      setAddUserId('');
      const list = await listUsersInGroup(usersDialogGroup.id);
      setUsersInGroup(Array.isArray(list) ? list : []);
    } catch (e) {
      logger.error('사용자 배정 실패:', e);
      setUsersDialogError(getErrorMessage(e, '사용자 배정에 실패했습니다.'));
    } finally {
      setUsersDialogActionId(null);
    }
  };

  const handleRemoveUserFromGroup = async (userId) => {
    if (!usersDialogGroup) return;
    setUsersDialogActionId(userId);
    setUsersDialogError(null);
    try {
      await removeUserFromGroup(usersDialogGroup.id, userId);
      const list = await listUsersInGroup(usersDialogGroup.id);
      setUsersInGroup(Array.isArray(list) ? list : []);
    } catch (e) {
      logger.error('사용자 제거 실패:', e);
      setUsersDialogError(getErrorMessage(e, '사용자 제거에 실패했습니다.'));
    } finally {
      setUsersDialogActionId(null);
    }
  };

  const alreadyInGroup = (userId) => usersInGroup.some((u) => (u.userId || u.username) === userId);
  const addableUsers = userList.filter((u) => {
    const id = u.userId ?? u.username;
    return id && !alreadyInGroup(id);
  });

  if (!isAdmin) {
    return (
      <div className="permission-group-management">
        <h2>권한 그룹 관리</h2>
        <p className="user-management-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="permission-group-management">
      <h2>권한 그룹 관리</h2>
      <p className="permission-group-hint">권한 그룹을 추가·수정·삭제하고, 그룹별 사용자를 할당할 수 있습니다.</p>
      {error && (
        <div className="user-management-error" role="alert">
          {error}
        </div>
      )}
      <div className="permission-group-actions">
        <button type="button" className="user-management-btn add" onClick={() => { setCreateOpen(true); setError(null); }} aria-label="권한 그룹 추가">
          권한 그룹 추가
        </button>
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
                <button type="button" className="user-management-btn add" onClick={() => openEdit(row)} aria-label={`수정, ${row.code}`}>수정</button>
                <button type="button" className="user-management-btn remove" onClick={() => openDelete(row)} aria-label={`삭제, ${row.code}`}>삭제</button>
                <button type="button" className="user-management-btn add" onClick={() => openUsersDialog(row)} aria-label={`사용자 관리, ${row.code}`}>사용자 관리</button>
              </td>
            </tr>
          ))
        )}
      </DataTable>

      {createOpen && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-create-title">
          <div className="permission-group-dialog">
            <h3 id="dialog-create-title">권한 그룹 추가</h3>
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
              <div className="permission-group-dialog-actions">
                <button type="submit" className="user-management-btn add" disabled={!!actionId}>{(actionId === 'create') ? '처리 중...' : '추가'}</button>
                <button type="button" className="user-management-btn" onClick={() => { setCreateOpen(false); setError(null); }}>취소</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editOpen && editGroup && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-edit-title">
          <div className="permission-group-dialog">
            <h3 id="dialog-edit-title">권한 그룹 수정</h3>
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
              <div className="permission-group-dialog-actions">
                <button type="submit" className="user-management-btn add" disabled={!!actionId}>{(actionId === 'edit') ? '처리 중...' : '저장'}</button>
                <button type="button" className="user-management-btn" onClick={() => { setEditOpen(false); setEditGroup(null); setError(null); }}>취소</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deleteOpen && groupToDelete && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-delete-title">
          <div className="permission-group-dialog">
            <h3 id="dialog-delete-title">권한 그룹 삭제</h3>
            <p>삭제하시겠습니까? 사용자가 할당되어 있으면 삭제할 수 없습니다. &quot;{groupToDelete.name}&quot; ({groupToDelete.code})</p>
            <div className="permission-group-dialog-actions">
              <button type="button" className="user-management-btn remove" onClick={handleDeleteConfirm} disabled={!!actionId} aria-label="삭제 확인">
                {(actionId === 'delete') ? '처리 중...' : '삭제'}
              </button>
              <button type="button" className="user-management-btn" onClick={() => { setDeleteOpen(false); setGroupToDelete(null); setError(null); }}>취소</button>
            </div>
          </div>
        </div>
      )}

      {usersDialogOpen && usersDialogGroup && (
        <div className="permission-group-dialog-overlay" role="dialog" aria-modal="true" aria-labelledby="dialog-users-title">
          <div className="permission-group-dialog permission-group-dialog-wide">
            <h3 id="dialog-users-title">사용자 할당 — {usersDialogGroup.name} ({usersDialogGroup.code})</h3>
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
                    <option key={id} value={id}>
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
                        <th scope="col">역할</th>
                        <th scope="col">동작</th>
                      </tr>
                    </thead>
                    <tbody>
                      {usersInGroup.length === 0 ? (
                        <tr><td colSpan={3} className="no-data">배정된 사용자가 없습니다.</td></tr>
                      ) : (
                        usersInGroup.map((u) => {
                          const uid = u.userId ?? u.username;
                          return (
                            <tr key={uid}>
                              <td>{uid}</td>
                              <td>{u.role || '-'}</td>
                              <td>
                                <button type="button" className="user-management-btn remove" onClick={() => handleRemoveUserFromGroup(uid)} disabled={usersDialogActionId === uid} aria-label={`제거, ${uid}`}>
                                  {usersDialogActionId === uid ? '처리 중...' : '제거'}
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
            <div className="permission-group-dialog-actions" style={{ marginTop: '1rem' }}>
              <button type="button" className="user-management-btn" onClick={() => { setUsersDialogOpen(false); setUsersDialogGroup(null); setUsersInGroup([]); setUsersDialogError(null); }}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PermissionGroupManagement;
