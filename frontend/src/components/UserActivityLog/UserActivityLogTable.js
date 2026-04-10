import React, { useState, useMemo } from 'react';
import DataTable, { EmptyTableBody } from '../DataTable';
import { getActivityActionTypeLabel } from '../../utils/activityActionTypeOptions';
import './UserActivityLog.css';

const ACTIVITY_LOG_COLUMNS = [
  { key: 'id', label: 'ID', sortable: true },
  { key: 'userId', label: '사용자 ID', sortable: true },
  { key: 'username', label: '사용자명', sortable: true },
  { key: 'action_type', label: '액션 타입', sortable: true },
  { key: 'ip_address', label: 'IP 주소', sortable: true },
  { key: 'request_path', label: '요청 경로', sortable: false },
  { key: 'response_status', label: '응답 상태', sortable: true },
  { key: 'response_time', label: '응답 시간', sortable: true },
  { key: 'result', label: '결과', sortable: false },
  { key: 'created_at', label: '생성일시', sortable: true },
];

const UserActivityLogTable = ({
  logs,
  onRowClick,
  loading,
  currentPage = 1,
  totalPages = 1,
  onPageChange,
  totalCount = 0,
  pageSize = 20,
  onPageSizeChange,
  actionTypeLabelMap = {},
}) => {
  const [sortConfig, setSortConfig] = useState({ key: 'created_at', direction: 'desc' });

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const sortedLogs = useMemo(() => {
    if (!logs.length || !sortConfig.key) return logs;
    const key = sortConfig.key;
    const dir = sortConfig.direction === 'asc' ? 1 : -1;
    return [...logs].sort((a, b) => {
      let va = key === 'userId' ? (a.userId ?? a.user_id) : a[key];
      let vb = key === 'userId' ? (b.userId ?? b.user_id) : b[key];
      if (key === 'response_time') {
        va = a.response_time_ms ?? va;
        vb = b.response_time_ms ?? vb;
      }
      if (va == null && vb == null) return 0;
      if (va == null) return dir;
      if (vb == null) return -dir;
      if (typeof va === 'number' && typeof vb === 'number') return dir * (va - vb);
      return dir * String(va).localeCompare(String(vb));
    });
  }, [logs, sortConfig.key, sortConfig.direction]);
  const formatDateTime = (dateTimeStr) => {
    if (!dateTimeStr) return '-';
    try {
      const date = new Date(dateTimeStr);
      return date.toLocaleString('ko-KR');
    } catch (e) {
      return dateTimeStr;
    }
  };

  const getSuccessBadge = (success) => {
    return success ? (
      <span className="badge badge-success">성공</span>
    ) : (
      <span className="badge badge-error">실패</span>
    );
  };

  const hasData = sortedLogs && sortedLogs.length > 0;
  const emptyMessage = '조회된 활동 이력이 없습니다.';
  const pagination = {
    currentPage,
    totalPages,
    onPageChange,
    simple: true,
    infoText: `총 ${totalCount.toLocaleString()}건`,
  };

  return (
    <div role="region" aria-label="활동 이력 테이블" aria-busy={loading}>
      <DataTable
        columns={ACTIVITY_LOG_COLUMNS}
        sortConfig={sortConfig}
        onSort={handleSort}
        loading={loading}
        emptyMessage={emptyMessage}
        emptyColSpan={ACTIVITY_LOG_COLUMNS.length}
        ariaLabel="활동 이력 테이블"
        pagination={pagination}
        pageSize={pageSize}
        onPageSizeChange={onPageSizeChange}
      >
        {!hasData ? (
          <EmptyTableBody colSpan={ACTIVITY_LOG_COLUMNS.length} message={emptyMessage} />
        ) : (
          sortedLogs.map((log) => (
            <tr
              key={log.id}
              onClick={() => onRowClick && onRowClick(log)}
              className="activity-log-table-row"
            >
              <td>{log.id}</td>
              <td>{log.userId ?? log.user_id ?? '-'}</td>
              <td>{log.username || '-'}</td>
              <td>{getActivityActionTypeLabel(log.action_type, actionTypeLabelMap)}</td>
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
