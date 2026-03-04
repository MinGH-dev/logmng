/**
 * Single permission group selector per user.
 * Each user can have at most one permission group (req 20250304).
 * Used in UserManagement per-user row.
 */
import React, { useState } from 'react';
import { Tooltip } from '@mui/material';
import { addUserToGroup, removeUserFromGroup } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import { ACTION_DISABLED_TOOLTIPS } from '../../constants/screenFunctionDescriptions';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import './UserGroupAssignment.css';

const UserGroupAssignment = ({ userId, userGroups = [], allGroups = [], onRefresh, disabled }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const currentGroup = (userGroups || [])[0] || null;
  const currentGroupId = currentGroup ? String(currentGroup.id ?? currentGroup.permission_group_id ?? '') : '';

  const handleChange = async (e) => {
    const newGroupId = e.target.value;
    if (newGroupId === currentGroupId) return;

    setLoading(true);
    setError(null);
    try {
      if (newGroupId === '') {
        if (currentGroup) {
          const gid = currentGroup.id ?? currentGroup.permission_group_id;
          await removeUserFromGroup(Number(gid), userId);
        }
      } else {
        await addUserToGroup(Number(newGroupId), userId);
      }
      if (typeof onRefresh === 'function') onRefresh();
    } catch (e) {
      logger.error('권한 그룹 변경 실패:', e);
      setError(getErrorMessage(e, '권한 그룹 변경에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="user-group-assignment">
      <div className="user-group-select-wrapper">
        <Tooltip title={disabled ? ACTION_DISABLED_TOOLTIPS.write : ''}>
          <span>
            <select
              className="user-group-select"
              value={currentGroupId}
              onChange={handleChange}
              disabled={disabled || loading}
              aria-label="권한 그룹 선택"
              aria-disabled={disabled}
            >
              <option value="">— 없음 —</option>
              {(allGroups || []).map((g) => (
                <option key={g.id} value={String(g.id)}>
                  {g.name || g.code} ({g.code})
                </option>
              ))}
            </select>
          </span>
        </Tooltip>
        {loading && <span className="user-group-loading">처리 중...</span>}
      </div>
      {error && (
        <div className="user-management-error user-group-assignment-error" role="alert">
          {error}
        </div>
      )}
    </div>
  );
};

export default UserGroupAssignment;
