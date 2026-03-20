import React, { useState, useEffect, useCallback } from 'react';
import { getUsers } from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import { getAllowedScreenIds, getScreenFunctions } from '../../utils/security';
import logger from '../../utils/logger';
import UserGroupAssignment from '../UserGroupAssignment/UserGroupAssignment';
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
  onRefresh,
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
                          <th scope="col">사용자명</th>
                          <th scope="col">사용자 ID</th>
                          <th scope="col">직급</th>
                          <th scope="col">직책</th>
                          <th scope="col">권한 그룹</th>
                          <th scope="col">결재자 여부</th>
                        </tr>
                      </thead>
                      <tbody>
                        {users.map((u) => {
                          const uid = u.userId ?? u.username;
                          const isApprover = usersWithApprover?.get(uid) === true;
                          return renderUserRow(u, isApprover, allGroups, onRefresh);
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

const UserManagement = ({ user }) => {
  const [tree, setTree] = useState([]);
  const [usersWithApprover, setUsersWithApprover] = useState(new Map());
  const [allGroups, setAllGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [expandedCodes, setExpandedCodes] = useState(() => new Set());

  const ids = getAllowedScreenIds(user);
  const screenFunctions = getScreenFunctions(user);
  const canAccessUserManagement =
    user?.isSystemAdmin === true ||
    (Array.isArray(ids) &&
      (ids.includes('user-management') || ids.includes('user-permission-hierarchy')));
  const canWrite =
    screenFunctions?.['user-management']?.write === true ||
    screenFunctions?.['user-permission-hierarchy']?.write === true;

  const loadHierarchy = useCallback(async () => {
    if (!canAccessUserManagement) return;
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

  const renderUserRow = (u, isApprover, allGroups, onRefresh) => {
    const userId = u.userId ?? u.username;
    const displayName = u.userName ?? userId;
    const rank = u.rank ?? '-';
    const position = u.position ?? '-';
    const permissionGroups = u.permissionGroups || [];
    const isSystemAdmin = u.isSystemAdmin === true || u.is_system_admin === true;

    return (
      <tr key={userId}>
        <td>{displayName}</td>
        <td>
          {userId}
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
            allGroups={allGroups}
            onRefresh={onRefresh}
            disabled={!canWrite}
          />
        </td>
        <td>{isApprover ? '예' : '아니오'}</td>
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

  return (
    <div className="user-management">
      <h2>사용자 관리</h2>
      <p className="user-permission-hierarchy-hint">
        부서를 펼치면 해당 부서의 사용자를 볼 수 있습니다. 권한 그룹, 결재자 여부를 편집할 수 있습니다.
      </p>
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
              onRefresh={loadHierarchy}
            />
          )}
        </section>
      </div>
    </div>
  );
};

export default UserManagement;
