import React, { useState, useEffect, useCallback } from 'react';
import { getUsers, addApprover, removeApprover, updateUserRole } from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import UserGroupAssignment from '../UserGroupAssignment/UserGroupAssignment';
import '../DepartmentApproverManagement/DepartmentApproverManagement.css';
import '../UserPermissionHierarchy/UserPermissionHierarchy.css';
import './UserManagement.css';

const HierarchyTree = ({
  nodes,
  expandedCodes,
  onToggle,
  level = 0,
  isRoot = false,
  renderUserRow,
  usersWithApprover,
  allGroups,
  onRoleChange,
  onRefresh,
  actionId,
}) => {
  if (!nodes || nodes.length === 0) return null;
  return (
    <ul
      className="dept-tree-list"
      role={isRoot ? 'tree' : 'group'}
      aria-label={isRoot ? '부서별 사용자 계층' : undefined}
    >
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
                          <th scope="col">사용자 ID</th>
                          <th scope="col">역할</th>
                          <th scope="col">권한 그룹</th>
                          <th scope="col">결재자 여부</th>
                          <th scope="col">동작</th>
                        </tr>
                      </thead>
                      <tbody>
                        {users.map((u) => {
                          const uid = u.userId ?? u.username;
                          const isApprover = usersWithApprover?.get(uid) === true;
                          return renderUserRow(u, isApprover, allGroups, onRoleChange, onRefresh, actionId);
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
                {!hasUsers && (
                  <p className="hierarchy-node-empty-users">해당 부서 사용자 없음</p>
                )}
                {hasChildren && (
                  <HierarchyTree
                    nodes={node.children}
                    expandedCodes={expandedCodes}
                    onToggle={onToggle}
                    level={level + 1}
                    isRoot={false}
                    renderUserRow={renderUserRow}
                    usersWithApprover={usersWithApprover}
                    allGroups={allGroups}
                    onRoleChange={onRoleChange}
                    onRefresh={onRefresh}
                    actionId={actionId}
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

const UserManagement = ({ onShowDepartmentApprovers, user }) => {
  const [tree, setTree] = useState([]);
  const [usersWithApprover, setUsersWithApprover] = useState(new Map());
  const [allGroups, setAllGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionId, setActionId] = useState(null);
  const [expandedCodes, setExpandedCodes] = useState(() => new Set());

  const isAdmin = user?.role === 'ADMIN';

  const loadHierarchy = useCallback(async () => {
    if (!isAdmin) return;
    setLoading(true);
    setError(null);
    try {
      const [hierarchyRes, usersRes, groupsRes] = await Promise.all([
        getUserPermissionHierarchy('tree'),
        getUsers(),
        listPermissionGroups(),
      ]);
      const hierarchyData = hierarchyRes.data;
      setTree(Array.isArray(hierarchyData) ? hierarchyData : (hierarchyData?.data || []));

      const usersData = usersRes.data;
      const usersList = Array.isArray(usersData) ? usersData : (usersData?.data || []);
      const approverMap = new Map();
      usersList.forEach((u) => {
        const id = u.userId ?? u.username;
        if (id) approverMap.set(id, u.isApprover === true);
      });
      setUsersWithApprover(approverMap);

      const groups = Array.isArray(groupsRes) ? groupsRes : (groupsRes?.data || []);
      setAllGroups(Array.isArray(groups) ? groups : []);
    } catch (e) {
      logger.error('사용자 관리 데이터 조회 실패:', e);
      setError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '목록을 불러오지 못했습니다.'));
      setTree([]);
      setUsersWithApprover(new Map());
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

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

  const handleRoleChange = async (userId, newRole) => {
    setActionId(`role-${userId}`);
    setError(null);
    try {
      await updateUserRole(userId, newRole);
      await loadHierarchy();
    } catch (e) {
      logger.error('역할 변경 실패:', e);
      setError(getErrorMessage(e, '역할 변경에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleAddApprover = async (userId) => {
    setActionId(`approver-add-${userId}`);
    setError(null);
    try {
      await addApprover(userId);
      await loadHierarchy();
    } catch (e) {
      logger.error('결재자 지정 실패:', e);
      setError(e.status === 403 ? '권한이 없습니다.' : e.status === 404 ? '해당 사용자를 찾을 수 없습니다.' : (e.message || '결재자 지정에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleRemoveApprover = async (userId) => {
    setActionId(`approver-remove-${userId}`);
    setError(null);
    try {
      await removeApprover(userId);
      await loadHierarchy();
    } catch (e) {
      logger.error('결재자 해제 실패:', e);
      setError(e.status === 403 ? '권한이 없습니다.' : e.status === 404 ? '해당 사용자를 찾을 수 없습니다.' : (e.message || '결재자 해제에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const renderUserRow = (u, isApprover, allGroups, onRoleChange, onRefresh, actionId) => {
    const userId = u.userId ?? u.username;
    const role = u.role || 'USER';
    const permissionGroups = u.permissionGroups || [];
    const isApproverAdding = actionId === `approver-add-${userId}`;
    const isApproverRemoving = actionId === `approver-remove-${userId}`;

    return (
      <tr key={userId}>
        <td>{userId}</td>
        <td>
          <select
            value={role}
            onChange={(e) => onRoleChange(userId, e.target.value)}
            disabled={!!actionId}
            aria-label={`역할 변경, ${userId}`}
          >
            <option value="ADMIN">ADMIN</option>
            <option value="USER">USER</option>
          </select>
        </td>
        <td>
          <UserGroupAssignment
            userId={userId}
            userGroups={permissionGroups}
            allGroups={allGroups}
            onRefresh={onRefresh}
            disabled={!!actionId}
          />
        </td>
        <td>{isApprover ? '예' : '아니오'}</td>
        <td>
          {isApprover ? (
            <button
              type="button"
              className="user-management-btn remove"
              onClick={() => handleRemoveApprover(userId)}
              disabled={!!actionId}
              aria-label={`결재자 해제, ${userId}`}
            >
              {isApproverRemoving ? '처리 중...' : '결재자 해제'}
            </button>
          ) : (
            <button
              type="button"
              className="user-management-btn add"
              onClick={() => handleAddApprover(userId)}
              disabled={!!actionId}
              aria-label={`결재자 지정, ${userId}`}
            >
              {isApproverAdding ? '처리 중...' : '결재자 지정'}
            </button>
          )}
        </td>
      </tr>
    );
  };

  if (!isAdmin) {
    return (
      <div className="user-management">
        <h2>사용자 관리</h2>
        <p className="user-management-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="user-management">
      <h2>사용자 관리</h2>
      <p className="user-permission-hierarchy-hint">
        부서를 펼치면 해당 부서의 사용자를 볼 수 있습니다. 역할, 권한 그룹, 결재자 여부를 편집할 수 있습니다.
      </p>
      {onShowDepartmentApprovers && (
        <button type="button" className="activity-log-button" style={{ marginBottom: '0.5rem' }} onClick={onShowDepartmentApprovers}>
          부서별 결재자 지정
        </button>
      )}
      {error && (
        <div className="user-management-error" role="alert">
          {error}
        </div>
      )}
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
              usersWithApprover={usersWithApprover}
              allGroups={allGroups}
              onRoleChange={handleRoleChange}
              onRefresh={loadHierarchy}
              actionId={actionId}
            />
          )}
        </section>
      </div>
    </div>
  );
};

export default UserManagement;
