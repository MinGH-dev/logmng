import React, { useState, useEffect } from 'react';
import { getUsers, addApprover, removeApprover } from '../../services/userService';
import DataTable, { EmptyTableBody } from '../DataTable';
import logger from '../../utils/logger';
import './UserManagement.css';

const USER_MANAGEMENT_COLUMNS = [
  { key: 'userId', label: '사용자 ID', sortable: false },
  { key: 'role', label: '역할', sortable: false },
  { key: 'departmentCode', label: '부서코드', sortable: false },
  { key: 'isApprover', label: '결재자 여부', sortable: false },
  { key: 'actions', label: '동작', sortable: false },
];

const UserManagement = ({ onShowDepartmentApprovers, user }) => {
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionId, setActionId] = useState(null);

  const isAdmin = user?.role === 'ADMIN';

  const loadList = async () => {
    if (!isAdmin) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getUsers();
      const data = result.data;
      const rows = Array.isArray(data) ? data : (data?.data || []);
      setList(rows);
    } catch (e) {
      logger.error('사용자 목록 조회 실패:', e);
      setError(e.status === 403 ? '관리자만 접근할 수 있습니다.' : (e.message || '목록을 불러오지 못했습니다.'));
      setList([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadList();
  }, [isAdmin]);

  const handleAddApprover = async (userId) => {
    setActionId(userId);
    setError(null);
    try {
      await addApprover(userId);
      await loadList();
    } catch (e) {
      logger.error('결재자 지정 실패:', e);
      setError(e.status === 403 ? '권한이 없습니다.' : e.status === 404 ? '해당 사용자를 찾을 수 없습니다.' : (e.message || '결재자 지정에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleRemoveApprover = async (userId) => {
    setActionId(userId);
    setError(null);
    try {
      await removeApprover(userId);
      await loadList();
    } catch (e) {
      logger.error('결재자 해제 실패:', e);
      setError(e.status === 403 ? '권한이 없습니다.' : e.status === 404 ? '해당 사용자를 찾을 수 없습니다.' : (e.message || '결재자 해제에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
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
      {onShowDepartmentApprovers && (
        <button type="button" className="activity-log-button" style={{ marginBottom: '0.5rem' }} onClick={onShowDepartmentApprovers}>
          부서별 결재자 지정
        </button>
      )}
      {error && <div className="user-management-error">{error}</div>}
      <DataTable
        columns={USER_MANAGEMENT_COLUMNS}
        loading={loading}
        emptyMessage="등록된 사용자가 없습니다."
        emptyColSpan={5}
        ariaLabel="사용자 목록"
      >
        {list.length === 0 ? (
          <EmptyTableBody colSpan={5} message="등록된 사용자가 없습니다." />
        ) : (
          list.map((row) => {
            const userId = row.userId ?? row.username;
            const isApprover = row.isApprover === true;
            return (
              <tr key={userId}>
                <td>{userId}</td>
                <td>{row.role || '-'}</td>
                <td>{row.departmentCode ?? row.department_code ?? '-'}</td>
                <td>{isApprover ? '예' : '아니오'}</td>
                <td>
                  {isApprover ? (
                    <button type="button" className="user-management-btn remove" onClick={() => handleRemoveApprover(userId)} disabled={actionId === userId} aria-label={`결재자 해제, ${userId}`}>
                      {actionId === userId ? '처리 중...' : '결재자 해제'}
                    </button>
                  ) : (
                    <button type="button" className="user-management-btn add" onClick={() => handleAddApprover(userId)} disabled={actionId === userId} aria-label={`결재자 지정, ${userId}`}>
                      {actionId === userId ? '처리 중...' : '결재자 지정'}
                    </button>
                  )}
                </td>
              </tr>
            );
          })
        )}
      </DataTable>
    </div>
  );
};

export default UserManagement;
