import React from 'react';
import DataTable, { EmptyTableBody } from '../DataTable';
import './UserActivityLog.css';

const ACTIVITY_LOG_COLUMNS = [
  { key: 'id', label: 'ID', sortable: false },
  { key: 'user_id', label: '사용자 ID', sortable: false },
  { key: 'username', label: '사용자명', sortable: false },
  { key: 'action_type', label: '액션 타입', sortable: false },
  { key: 'ip_address', label: 'IP 주소', sortable: false },
  { key: 'request_path', label: '요청 경로', sortable: false },
  { key: 'response_status', label: '응답 상태', sortable: false },
  { key: 'response_time', label: '응답 시간', sortable: false },
  { key: 'result', label: '결과', sortable: false },
  { key: 'created_at', label: '생성일시', sortable: false },
];

const UserActivityLogTable = ({ logs, onRowClick, loading }) => {
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

  const hasData = logs && logs.length > 0;
  const emptyMessage = '조회된 활동 이력이 없습니다.';

  return (
    <div role="region" aria-label="활동 이력 테이블" aria-busy={loading}>
      <DataTable
        columns={ACTIVITY_LOG_COLUMNS}
        loading={loading}
        emptyMessage={emptyMessage}
        emptyColSpan={ACTIVITY_LOG_COLUMNS.length}
        ariaLabel="활동 이력 테이블"
      >
        {!hasData ? (
          <EmptyTableBody colSpan={ACTIVITY_LOG_COLUMNS.length} message={emptyMessage} />
        ) : (
          logs.map((log) => (
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
          ))
        )}
      </DataTable>
    </div>
  );
};

export default UserActivityLogTable;
