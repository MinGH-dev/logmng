import React from 'react';
import { format } from 'date-fns';
import DataTable, { EmptyTableBody } from './DataTable';
import './LogTable.css';

const LOG_COLUMNS = [
  { key: 'log_timestamp', label: 'log_timestamp', sortable: true },
  { key: 'media_code', label: 'media_code', sortable: true },
  { key: 'tr_code', label: 'tr_code', sortable: true },
  { key: 'brodid', label: 'brodid', sortable: true },
  { key: 'msg_code', label: 'msg_code', sortable: true },
  { key: 'bmsg', label: 'bmsg', sortable: true },
  { key: 'log_ch_cd', label: 'log_ch_cd', sortable: true },
  { key: 'log_io_cd', label: 'log_io_cd', sortable: true },
  { key: 'pub_ip', label: 'pub_ip', sortable: true },
  { key: 'prt_ip', label: 'prt_ip', sortable: true },
  { key: 'term_no', label: 'term_no', sortable: true },
  { key: 'data', label: 'data', sortable: false },
];

const LogTable = ({
  logs,
  loading,
  sortConfig,
  onSort,
  currentPage,
  totalPages,
  totalCount = 0,
  onPageChange,
  pageSize = 20,
  onPageSizeChange,
  keywords = [],
}) => {
  const formatTime = (timeString) => {
    if (!timeString) return '';
    if (typeof timeString === 'string' && timeString.length === 9 && /^\d{9}$/.test(timeString)) {
      const hours = timeString.substring(0, 2);
      const minutes = timeString.substring(2, 4);
      const seconds = timeString.substring(4, 6);
      const milliseconds = timeString.substring(6, 9);
      return `${hours}:${minutes}:${seconds}.${milliseconds}`;
    }
    if (typeof timeString === 'string' && timeString.includes('T')) {
      try {
        const date = new Date(timeString);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
      } catch (error) {
        return timeString;
      }
    }
    try {
      return format(new Date(timeString), 'yyyy-MM-dd HH:mm:ss');
    } catch (error) {
      return timeString;
    }
  };

  const highlightKeywords = (text, kw) => {
    if (!kw || kw.length === 0 || typeof text !== 'string') return text;
    const keyword = kw[0];
    if (!keyword || keyword.trim() === '') return text;
    const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
    const parts = text.split(regex);
    return parts.map((part, index) => {
      if (regex.test(part)) {
        return <span key={`keyword-${index}`} className="highlight-keyword">{part}</span>;
      }
      return part;
    });
  };

  const effectiveSortConfig = sortConfig && sortConfig.key ? sortConfig : null;
  const pagination = {
    currentPage,
    totalPages,
    onPageChange,
    infoText: `총 ${totalCount.toLocaleString()}건`,
  };

  return (
    <DataTable
      columns={LOG_COLUMNS}
      sortConfig={effectiveSortConfig}
      onSort={onSort}
      loading={loading}
      emptyMessage="검색 결과가 없습니다."
      emptyColSpan={12}
      pagination={pagination}
      pageSize={pageSize}
      onPageSizeChange={onPageSizeChange}
      ariaLabel="로그 검색 결과"
    >
      {logs.length === 0 ? (
        <EmptyTableBody colSpan={12} message="검색 결과가 없습니다." />
      ) : (
        logs.map((log, index) => (
          <tr key={`log-${index}-${log.log_type || 'unknown'}-${log.log_timestamp || log.timestamp || index}`}>
            <td>{formatTime(log.log_timestamp || log.timestamp || log.prc_time || log.log_time)}</td>
            <td>{log.media_code || log.media_gb || log.mediaCode}</td>
            <td>{log.tr_code || log.trCode}</td>
            <td>{log.user_id || log.loginId || log.brodid}</td>
            <td>{log.status_code || log.msg_code}</td>
            <td>{log.error_message || log.bmsg}</td>
            <td>{log.device_type || log.log_ch_cd}</td>
            <td>{log.log_type || log.log_io_cd}</td>
            <td>{log.ip_address || log.pub_ip}</td>
            <td>{log.session_id || log.prt_ip}</td>
            <td>{log.response_time || log.term_no}</td>
            <td className="tr-data-cell">
              <span className="tr-data-text">
                {highlightKeywords(log.request_data || log.response_data || log.data || log.trData || log.decryptedData, keywords)}
              </span>
            </td>
          </tr>
        ))
      )}
    </DataTable>
  );
};

export default LogTable;
