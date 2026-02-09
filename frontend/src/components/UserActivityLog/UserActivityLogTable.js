import React from 'react';
import './UserActivityLog.css';

const UserActivityLogTable = ({ logs, onRowClick, loading }) => {
  if (loading) {
    return (
      <div className="activity-log-table-loading">
        <p>데이터를 불러오는 중...</p>
      </div>
    );
  }

  if (!logs || logs.length === 0) {
    return (
      <div className="activity-log-table-empty">
        <p>조회된 활동 이력이 없습니다.</p>
      </div>
    );
  }

  const formatDateTime = (dateTimeStr) => {
    if (!dateTimeStr) return '-';
    try {
      const date = new Date(dateTimeStr);
      return date.toLocaleString('ko-KR');
    } catch (e) {
      return dateTimeStr;
    }
  };

  const getActionTypeLabel = (actionType) => {
    const labels = {
      'LOGIN': '로그인',
      'LOGOUT': '로그아웃',
      'SEARCH': '검색',
      'VIEW': '조회',
      'DECRYPT': '복호화',
      'ADVANCED_SEARCH': '고급 검색',
      'EXPORT': '내보내기',
      'STATS_VIEW': '통계 조회',
      'SCHEMA_VIEW': '스키마 조회',
    };
    return labels[actionType] || actionType;
  };

  const getSuccessBadge = (success) => {
    return success ? (
      <span className="badge badge-success">성공</span>
    ) : (
      <span className="badge badge-error">실패</span>
    );
  };

  return (
    <div className="activity-log-table-container">
      <table className="activity-log-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>사용자 ID</th>
            <th>사용자명</th>
            <th>액션 타입</th>
            <th>IP 주소</th>
            <th>요청 경로</th>
            <th>응답 상태</th>
            <th>응답 시간</th>
            <th>결과</th>
            <th>생성일시</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr
              key={log.id}
              onClick={() => onRowClick && onRowClick(log)}
              className="activity-log-table-row"
            >
              <td>{log.id}</td>
              <td>{log.user_id || '-'}</td>
              <td>{log.username || '-'}</td>
              <td>{getActionTypeLabel(log.action_type)}</td>
              <td>{log.ip_address || '-'}</td>
              <td className="request-path-cell">
                {log.request_path ? (
                  <span title={log.request_path}>
                    {log.request_path.length > 50
                      ? log.request_path.substring(0, 50) + '...'
                      : log.request_path}
                  </span>
                ) : (
                  '-'
                )}
              </td>
              <td>{log.response_status || '-'}</td>
              <td>
                {log.response_time_ms != null
                  ? `${log.response_time_ms}ms`
                  : '-'}
              </td>
              <td>{getSuccessBadge(log.success)}</td>
              <td>{formatDateTime(log.created_at)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default UserActivityLogTable;





