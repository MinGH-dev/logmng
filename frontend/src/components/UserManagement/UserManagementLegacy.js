import React, { useState, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  TextField,
} from '@mui/material';
import { deleteUser } from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import { getAllowedScreenIds, getScreenFunctions } from '../../utils/security';
import logger from '../../utils/logger';
import UserGroupAssignment from '../UserGroupAssignment/UserGroupAssignment';
import ExternalProvisioning from './ExternalProvisioning';
import '../UserPermissionHierarchy/UserPermissionHierarchy.css';
import './UserManagement.css';

const USER_DELETE_CHANGE_REASON_MAX = 500;

const HierarchyTree = ({
  nodes,
  expandedCodes,
  onToggle,
  level = 0,
  isRoot = false,
  renderUserRow,
  allGroups,
  onRefresh,
}) => {
  if (!nodes || nodes.length === 0) return null;
  return (
    <ul className="dept-tree-list" role={isRoot ? 'tree' : 'group'} aria-label={isRoot ? '부서별 사용자 계층' : undefined}>
      {nodes.map((node) => {
        const code = node.code;
        const hasChildren = node.children && node.children.length > 0;
        const users = node.users || [];
        const hasUsers = users.length > 0;
        const canExpand = hasChildren || hasUsers;
        const isExpanded = expandedCodes.has(code);
        return (
          <li
            key={code}
            className="dept-tree-item"
            role="treeitem"
            aria-expanded={canExpand ? isExpanded : undefined}
            aria-selected={false}
            style={{ paddingLeft: `${level * 1.25}rem` }}
          >
            <div className="dept-tree-node-wrapper">
              {canExpand ? (
                <button
                  type="button"
                  className="dept-tree-toggle"
                  onClick={() => onToggle(code)}
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
              <span className="dept-tree-label">
                [{code}] {node.name || code}
              </span>
            </div>
            {canExpand && isExpanded && (
              <>
                {hasUsers && (
                  <div className="hierarchy-node-users">
                    <table className="log-table hierarchy-users-table" aria-label={`${code} 부서 사용자 목록`}>
                      <thead>
                        <tr>
                          <th scope="col">사용자명</th>
                          <th scope="col">사용자 ID</th>
                          <th scope="col">직급</th>
                          <th scope="col">직책</th>
                          <th scope="col">권한 그룹</th>
                          <th scope="col">작업</th>
                        </tr>
                      </thead>
                      <tbody>
                        {users.map((u) => renderUserRow(u, allGroups, onRefresh))}
                      </tbody>
                    </table>
                  </div>
                )}
                {!hasUsers && <p className="hierarchy-node-empty-users">해당 부서 사용자 없음</p>}
                {hasChildren && (
                  <HierarchyTree
                    nodes={node.children}
                    expandedCodes={expandedCodes}
                    onToggle={onToggle}
                    level={level + 1}
                    isRoot={false}
                    renderUserRow={renderUserRow}
                    allGroups={allGroups}
                    onRefresh={onRefresh}
                  />
                )}
              </>
            )}
          </li>
        );
      })}
    </ul>
  );
};

const UserManagementLegacy = ({ user }) => {
  const [tree, setTree] = useState([]);
  const [allGroups, setAllGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [expandedCodes, setExpandedCodes] = useState(() => new Set());
  const [provisionModalOpen, setProvisionModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteReason, setDeleteReason] = useState('');
  const [deleteDialogError, setDeleteDialogError] = useState(null);
  const [deleteSubmitting, setDeleteSubmitting] = useState(false);

  const ids = getAllowedScreenIds(user);
  const screenFunctions = getScreenFunctions(user);
  const canAccessUserManagement =
    user?.isSystemAdmin === true ||
    (Array.isArray(ids) && (ids.includes('user-management') || ids.includes('user-permission-hierarchy')));
  const canWrite =
    screenFunctions?.['user-management']?.write === true ||
    screenFunctions?.['user-permission-hierarchy']?.write === true;

  const loadHierarchy = useCallback(async () => {
    if (!canAccessUserManagement) return;
    setLoading(true);
    setError(null);
    try {
      const [hierarchyRes, groupsRes] = await Promise.all([
        getUserPermissionHierarchy('tree'),
        listPermissionGroups(),
      ]);
      const hierarchyData = hierarchyRes.data;
      setTree(Array.isArray(hierarchyData) ? hierarchyData : (hierarchyData?.data || []));

      const groups = Array.isArray(groupsRes) ? groupsRes : (groupsRes?.data || []);
      setAllGroups(Array.isArray(groups) ? groups : []);
    } catch (e) {
      logger.error('사용자 관리 데이터 조회 실패:', e);
      setError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '목록을 불러오지 못했습니다.'));
      setTree([]);
    } finally {
      setLoading(false);
    }
  }, [canAccessUserManagement]);

  useEffect(() => {
    loadHierarchy();
  }, [loadHierarchy]);

  const handleToggle = (code) => {
    setExpandedCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  const openDeleteDialog = (u) => {
    const rowUserId = u.userId ?? u.username;
    const displayUserId = u.employeeNumber ?? u.employee_number ?? rowUserId;
    const displayName = u.userName ?? rowUserId;
    setDeleteTarget({
      userId: rowUserId,
      displayName,
      displayUserId,
    });
    setDeleteReason('');
    setDeleteDialogError(null);
  };

  const closeDeleteDialog = () => {
    setDeleteTarget(null);
    setDeleteReason('');
    setDeleteDialogError(null);
    setDeleteSubmitting(false);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget?.userId) return;
    const trimmed = deleteReason.trim();
    if (!trimmed) {
      setDeleteDialogError('삭제 사유를 입력하세요.');
      return;
    }
    if (trimmed.length > USER_DELETE_CHANGE_REASON_MAX) {
      setDeleteDialogError(`사유는 ${USER_DELETE_CHANGE_REASON_MAX}자 이하여야 합니다.`);
      return;
    }
    setDeleteDialogError(null);
    setDeleteSubmitting(true);
    try {
      await deleteUser(deleteTarget.userId, { changeReason: trimmed });
      closeDeleteDialog();
      await loadHierarchy();
    } catch (e) {
      logger.error('사용자 삭제 실패:', e);
      setDeleteDialogError(getErrorMessage(e, '삭제에 실패했습니다.'));
    } finally {
      setDeleteSubmitting(false);
    }
  };

  const renderUserRow = (u, groups, onRefresh) => {
    const userId = u.userId ?? u.username;
    const displayUserId = u.employeeNumber ?? u.employee_number ?? userId;
    const displayName = u.userName ?? userId;
    const rank = u.rank ?? '-';
    const position = u.position ?? '-';
    const permissionGroups = u.permissionGroups || [];
    const isSystemAdmin = u.isSystemAdmin === true || u.is_system_admin === true;
    const canDeleteRow = canWrite && !isSystemAdmin;

    return (
      <tr key={userId}>
        <td>{displayName}</td>
        <td>
          {displayUserId}
          {isSystemAdmin && (
            <span className="system-admin-badge" aria-label="시스템 관리자">
              {' '}시스템 관리자
            </span>
          )}
        </td>
        <td>{rank}</td>
        <td>{position}</td>
        <td>
          <UserGroupAssignment
            userId={userId}
            userGroups={permissionGroups}
            allGroups={groups}
            onRefresh={onRefresh}
            disabled={!canWrite}
          />
        </td>
        <td>
          {canWrite && (
            <button
              type="button"
              className="user-management-btn remove"
              disabled={!canDeleteRow}
              title={isSystemAdmin ? '시스템 관리자는 삭제할 수 없습니다.' : '사용자 삭제'}
              aria-label={`${displayName} 사용자 삭제`}
              onClick={() => canDeleteRow && openDeleteDialog(u)}
            >
              삭제
            </button>
          )}
        </td>
      </tr>
    );
  };

  if (!canAccessUserManagement) {
    return (
      <div className="user-management">
        <h2>사용자 관리</h2>
        <p className="user-management-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  const provisionDialogTitleId = 'user-provision-dialog-title';
  const deleteDialogTitleId = 'user-delete-dialog-title';

  return (
    <div className="user-management">
      <div className="user-management-header">
        <h2>사용자 관리</h2>
        {canWrite && (
          <button
            type="button"
            className="user-management-btn user-management-btn-primary"
            onClick={() => setProvisionModalOpen(true)}
          >
            사용자 추가
          </button>
        )}
      </div>
      <p className="user-permission-hierarchy-hint">
        부서를 펼치면 해당 부서의 사용자를 볼 수 있습니다. 권한 그룹을 편집할 수 있습니다.
      </p>
      {error && <div className="user-management-error" role="alert">{error}</div>}
      <div className="user-permission-hierarchy-layout">
        <section className="user-permission-hierarchy-tree-section" aria-label="부서별 사용자 계층">
          {loading ? (
            <p aria-live="polite">목록을 불러오는 중…</p>
          ) : tree.length === 0 ? (
            <p>등록된 부서가 없습니다.</p>
          ) : (
            <HierarchyTree
              nodes={tree}
              expandedCodes={expandedCodes}
              onToggle={handleToggle}
              isRoot
              renderUserRow={renderUserRow}
              allGroups={allGroups}
              onRefresh={loadHierarchy}
            />
          )}
        </section>
      </div>

      {provisionModalOpen && (
        <Dialog
          open
          onClose={() => setProvisionModalOpen(false)}
          maxWidth="lg"
          fullWidth
          aria-labelledby={provisionDialogTitleId}
        >
          <DialogTitle id={provisionDialogTitleId}>인사정보에서 사용자 등록</DialogTitle>
          <DialogContent dividers>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              인사정보에서 직원·부서를 검색한 뒤 선택한 직원을 앱 사용자로 등록합니다.
            </Typography>
            <ExternalProvisioning embeddedInModal onProvisioned={loadHierarchy} />
          </DialogContent>
          <DialogActions>
            <Button type="button" onClick={() => setProvisionModalOpen(false)}>
              취소
            </Button>
          </DialogActions>
        </Dialog>
      )}

      {deleteTarget && (
        <Dialog
          open
          onClose={deleteSubmitting ? undefined : closeDeleteDialog}
          maxWidth="sm"
          fullWidth
          aria-labelledby={deleteDialogTitleId}
        >
          <DialogTitle id={deleteDialogTitleId}>사용자 삭제</DialogTitle>
          <DialogContent dividers>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              다음 사용자를 삭제합니다. 되돌릴 수 없을 수 있습니다. 사유는 감사 로그에 남습니다.
            </Typography>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              <strong>{deleteTarget.displayName}</strong>
              {' '}
              <span aria-label="표시 사용자 ID">({String(deleteTarget.displayUserId)})</span>
            </Typography>
            {deleteDialogError && (
              <div className="user-management-error user-management-delete-dialog-error" role="alert">
                {deleteDialogError}
              </div>
            )}
            <TextField
              label="삭제 사유"
              placeholder="필수 입력"
              value={deleteReason}
              onChange={(ev) => setDeleteReason(ev.target.value)}
              multiline
              minRows={4}
              fullWidth
              required
              inputProps={{
                maxLength: USER_DELETE_CHANGE_REASON_MAX,
                'aria-required': true,
              }}
              helperText={`${deleteReason.trim().length}/${USER_DELETE_CHANGE_REASON_MAX} (공백만 불가, 전송 시 trim)`}
            />
          </DialogContent>
          <DialogActions>
            <Button type="button" onClick={closeDeleteDialog} disabled={deleteSubmitting}>
              취소
            </Button>
            <Button
              type="button"
              color="error"
              variant="contained"
              onClick={handleDeleteConfirm}
              disabled={deleteSubmitting}
            >
              {deleteSubmitting ? '처리 중…' : '삭제'}
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </div>
  );
};

export default UserManagementLegacy;
