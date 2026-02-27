import React, { useState, useEffect, useCallback } from 'react';
import { getUserPermissionHierarchy } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import '../DepartmentApproverManagement/DepartmentApproverManagement.css';
import './UserPermissionHierarchy.css';

const HierarchyTree = ({ nodes, expandedCodes, onToggle, level = 0, isRoot = false }) => {
  if (!nodes || nodes.length === 0) return null;
  return (
    <ul
      className="dept-tree-list"
      role={isRoot ? 'tree' : 'group'}
      aria-label={isRoot ? '부서별 사용자 권한 계층' : undefined}
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
                        </tr>
                      </thead>
                      <tbody>
                        {users.map((u) => (
                          <tr key={u.userId}>
                            <td>{u.userId}</td>
                            <td>{u.role || '-'}</td>
                            <td>
                              {u.permissionGroups && u.permissionGroups.length > 0
                                ? u.permissionGroups.map((g) => g.name || g.code).join(', ')
                                : '-'}
                            </td>
                          </tr>
                        ))}
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

const UserPermissionHierarchy = ({ user }) => {
  const [tree, setTree] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [expandedCodes, setExpandedCodes] = useState(() => new Set());

  const isAdmin = user?.role === 'ADMIN';

  const loadHierarchy = useCallback(async () => {
    if (!isAdmin) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getUserPermissionHierarchy('tree');
      const data = result.data;
      setTree(Array.isArray(data) ? data : (data?.data || []));
    } catch (e) {
      logger.error('사용자 권한 계층 조회 실패:', e);
      setError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '계층 목록을 불러오지 못했습니다.'));
      setTree([]);
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

  if (!isAdmin) {
    return (
      <div className="user-permission-hierarchy">
        <h2>사용자 권한 계층</h2>
        <p className="department-approver-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="user-permission-hierarchy">
      <h2>사용자 권한 계층</h2>
      <p className="user-permission-hierarchy-hint">부서를 펼치면 해당 부서의 사용자와 권한 그룹을 볼 수 있습니다.</p>
      {error && (
        <div className="user-management-error" role="alert">
          {error}
        </div>
      )}
      {loading ? (
        <p>목록을 불러오는 중…</p>
      ) : tree.length === 0 ? (
        <p>등록된 부서가 없습니다.</p>
      ) : (
        <section className="department-tree-section hierarchy-tree-section" aria-label="부서별 사용자 권한 계층">
          <HierarchyTree nodes={tree} expandedCodes={expandedCodes} onToggle={handleToggle} isRoot />
        </section>
      )}
    </div>
  );
};

export default UserPermissionHierarchy;
