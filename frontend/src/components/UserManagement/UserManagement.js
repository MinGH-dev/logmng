import React, { useState, useEffect } from 'react';
import { getUsers, addApprover, removeApprover } from '../../services/userService';
import logger from '../../utils/logger';
import './UserManagement.css';

const UserManagement = ({ onBackToMain, user }) => {
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
        {onBackToMain && (
          <button type="button" className="back-button" onClick={onBackToMain}>
            ← 메인으로
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="user-management">
      <h2>사용자 관리</h2>
      {onBackToMain && (
        <button type="button" className="user-management-back back-button" onClick={onBackToMain}>
          ← 메인으로
        </button>
      )}
      {error && <div className="user-management-error">{error}</div>}
      {loading ? (
        <p>목록을 불러오는 중...</p>
      ) : list.length === 0 ? (
        <p>등록된 사용자가 없습니다.</p>
      ) : (
        <table className="user-management-table">
          <thead>
            <tr>
              <th>사용자 ID</th>
              <th>역할</th>
              <th>부서코드</th>
              <th>결재자 여부</th>
              <th>동작</th>
            </tr>
          </thead>
          <tbody>
            {list.map((row) => {
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
                      <button
                        type="button"
                        className="user-management-btn remove"
                        onClick={() => handleRemoveApprover(userId)}
                        disabled={actionId === userId}
                      >
                        {actionId === userId ? '처리 중...' : '결재자 해제'}
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="user-management-btn add"
                        onClick={() => handleAddApprover(userId)}
                        disabled={actionId === userId}
                      >
                        {actionId === userId ? '처리 중...' : '결재자 지정'}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default UserManagement;
