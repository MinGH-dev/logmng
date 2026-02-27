/**
 * Inline user-centric permission group add/remove.
 * Used in UserManagement per-user row. Reuses addUserToGroup, removeUserFromGroup.
 * §2.2 Architecture: UserGroupAssignment extraction.
 */
import React, { useState } from 'react';
import { addUserToGroup, removeUserFromGroup } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import './UserGroupAssignment.css';

const UserGroupAssignment = ({ userId, userGroups = [], allGroups = [], onRefresh, disabled }) => {
  const [addOpen, setAddOpen] = useState(false);
  const [addGroupId, setAddGroupId] = useState('');
  const [error, setError] = useState(null);
  const [actionId, setActionId] = useState(null);

  const userGroupIds = (userGroups || []).map((g) => g.id ?? g.permission_group_id).filter(Boolean);
  const addableGroups = (allGroups || []).filter((g) => !userGroupIds.includes(g.id));

  const handleAdd = async () => {
    if (!addGroupId || !userId) return;
    setActionId(addGroupId);
    setError(null);
    try {
      await addUserToGroup(Number(addGroupId), userId);
      setAddOpen(false);
      setAddGroupId('');
      if (typeof onRefresh === 'function') onRefresh();
    } catch (e) {
      logger.error('권한 그룹 추가 실패:', e);
      setError(getErrorMessage(e, '추가에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleRemove = async (groupId) => {
    if (!userId) return;
    setActionId(String(groupId));
    setError(null);
    try {
      await removeUserFromGroup(Number(groupId), userId);
      if (typeof onRefresh === 'function') onRefresh();
    } catch (e) {
      logger.error('권한 그룹 제거 실패:', e);
      setError(getErrorMessage(e, '제거에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  return (
    <div className="user-group-assignment">
      <div className="user-group-assignment-badges">
        {(userGroups || []).map((g) => {
          const gid = g.id ?? g.permission_group_id;
          const name = g.name || g.code || String(gid);
          return (
            <span key={gid} className="user-group-badge">
              {name}
              <button
                type="button"
                className="user-group-badge-remove"
                onClick={() => handleRemove(gid)}
                disabled={disabled || actionId === String(gid)}
                aria-label={`권한 그룹 제거, ${name}`}
              >
                ×
              </button>
            </span>
          );
        })}
        {addableGroups.length > 0 && (
          <>
            {!addOpen ? (
              <button
                type="button"
                className="user-management-btn add user-group-add-btn"
                onClick={() => { setAddOpen(true); setError(null); }}
                disabled={disabled}
                aria-label="권한 그룹 추가"
              >
                + 추가
              </button>
            ) : (
              <span className="user-group-add-inline">
                <select
                  value={addGroupId}
                  onChange={(e) => setAddGroupId(e.target.value)}
                  aria-label="추가할 권한 그룹 선택"
                  disabled={disabled}
                >
                  <option value="">— 그룹 선택 —</option>
                  {addableGroups.map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name || g.code} ({g.code})
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="user-management-btn add"
                  onClick={handleAdd}
                  disabled={!addGroupId || !!actionId}
                  aria-label="추가"
                >
                  {actionId ? '처리 중...' : '추가'}
                </button>
                <button
                  type="button"
                  className="user-management-btn"
                  onClick={() => { setAddOpen(false); setAddGroupId(''); setError(null); }}
                  aria-label="취소"
                >
                  취소
                </button>
              </span>
            )}
          </>
        )}
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
