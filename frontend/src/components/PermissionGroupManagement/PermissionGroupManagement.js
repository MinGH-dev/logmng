/**
 * Standalone wrapper for permission group management (h2 + panel).
 * Primary entry is UserPermissionHierarchy (single-screen); this component is kept
 * for sub-component reuse and when route redirects to hierarchy, this is not shown.
 */
import React from 'react';
import PermissionGroupPanel from './PermissionGroupPanel';
import { getAllowedScreenIds } from '../../utils/security';
import './PermissionGroupManagement.css';

const PermissionGroupManagement = ({ user, menuTree }) => {
  const ids = getAllowedScreenIds(user);
  const canAccessPermissionGroupManagement =
    user?.isSystemAdmin === true ||
    (Array.isArray(ids) &&
      (ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy')));

  if (!canAccessPermissionGroupManagement) {
    return (
      <div className="permission-group-management">
        <h2>권한 그룹 관리 v1.0.0</h2>
        <p className="user-management-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="permission-group-management">
      <h2>권한 그룹 관리 v1.0.0</h2>
      <p className="permission-group-hint">권한 그룹을 추가·수정·삭제하고, 그룹별 사용자를 할당할 수 있습니다.</p>
      <PermissionGroupPanel user={user} onRefreshHierarchy={undefined} menuTree={menuTree} />
    </div>
  );
};

export default PermissionGroupManagement;
