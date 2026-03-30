import React, { useState } from 'react';
import { format } from 'date-fns';
import DataTable, { EmptyTableBody } from './DataTable';
import './LogTable.css';

/** Legacy POST .../search columns + sort keys (pb-feplog). */
const LOG_COLUMNS_PB_FEP_LEGACY = [
  { key: 'log_timestamp', label: 'log_timestamp', sortable: true },
  { key: 'tr_code', label: 'tr_code', sortable: true },
  { key: 'user_id', label: 'user_id', sortable: true },
  { key: 'status_code', label: 'status_code', sortable: true },
  { key: 'error_message', label: 'error_message', sortable: true },
  { key: 'device_type', label: 'device_type', sortable: true },
  { key: 'log_type', label: 'log_type', sortable: true },
  { key: 'ip_address', label: 'ip_address', sortable: true },
  { key: 'session_id', label: 'session_id', sortable: true },
  { key: 'response_time', label: 'response_time', sortable: true },
  { key: 'data', label: 'data', sortable: false },
];

/** Wireframe SVG v10 — POST .../pb-fep-log-search row keys (expand chevron in log_timestamp cell). */
const LOG_COLUMNS_PB_FEP_SVG = [
  { key: 'log_timestamp', label: 'log_timestamp', sortable: true },
  { key: 'tr_code', label: 'tr_code', sortable: true },
  { key: 'login_id', label: 'login_id', sortable: true },
  { key: 'msg_code', label: 'msg_code', sortable: true },
  { key: 'bmsg', label: 'bmsg', sortable: true },
  { key: 'log_ch_cd', label: 'log_ch_cd', sortable: true },
  { key: 'send_recv', label: 'send_recv', sortable: true },
  { key: 'src_ip', label: 'src_ip', sortable: true },
  { key: 'dest_ip', label: 'dest_ip', sortable: true },
  { key: 'app_id', label: 'app_id', sortable: true },
  { key: 'data', label: 'data', sortable: false },
];

export function getPbFeplogRowKey(log) {
  const lt = log.log_type != null ? String(log.log_type) : 'na';
  const id =
    log.id != null
      ? String(log.id)
      : `${log.log_timestamp}-${log.tr_code}-${log.user_id ?? log.login_id ?? ''}`;
  return `${lt}-${id}`;
}

const LogTable = ({
  logs,
  loading,
  sortConfig,
  sortCriteria = null,
  onSort,
  currentPage,
  totalPages,
  totalCount = 0,
  onPageChange,
  pageSize = 25,
  onPageSizeChange,
  keywords = [],
  expandedRowKeys = null,
  onRowExpandChange,
  layoutVariant = 'default',
  dataTableContainerClassName = '',
  dataTablePaginationFooterOrder = 'default',
  tableClassName = '',
}) => {
  const [internalExpanded, setInternalExpanded] = useState(() => new Set());
  const expandedRows = expandedRowKeys != null ? expandedRowKeys : internalExpanded;
  const controlled = expandedRowKeys != null && typeof onRowExpandChange === 'function';

  const isPbFepSvg = layoutVariant === 'pb-fep-svg';
  const columns = isPbFepSvg ? LOG_COLUMNS_PB_FEP_SVG : LOG_COLUMNS_PB_FEP_LEGACY;
  const colCount = columns.length;

  const toggleRowExpanded = (rowKey) => {
    const base = expandedRowKeys != null ? expandedRowKeys : internalExpanded;
    const next = new Set(base);
    const wasExpanded = next.has(rowKey);
    if (wasExpanded) {
      next.delete(rowKey);
    } else {
      next.add(rowKey);
    }
    if (controlled) {
      onRowExpandChange(next, { manualCollapse: wasExpanded });
    } else {
      setInternalExpanded(next);
    }
  };

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

  const highlightKeywords = (text, kwList) => {
    if (!kwList || kwList.length === 0 || typeof text !== 'string') return text;
    const parts = kwList.map((k) => String(k).trim()).filter(Boolean);
    if (parts.length === 0) return text;
    const escaped = parts.map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
    const regex = new RegExp(`(${escaped})`, 'gi');
    const split = text.split(regex);
    return split.map((part, index) => {
      if (parts.some((p) => p.toLowerCase() === part.toLowerCase())) {
        return <span key={`keyword-${index}`} className="highlight-keyword">{part}</span>;
      }
      return part;
    });
  };

  const effectiveSortConfig = sortCriteria != null && sortCriteria.length > 0 ? null : (sortConfig && sortConfig.key ? sortConfig : null);
  const pagination = {
    currentPage,
    totalPages,
    onPageChange,
    infoText: `총 ${totalCount.toLocaleString()}건`,
  };

  const streamPayload = (log) => {
    if (isPbFepSvg) {
      return log.data ?? log.request_data ?? log.response_data ?? '';
    }
    return log.request_data || log.response_data || log.data || log.trData || log.decryptedData || '';
  };

  const renderStreamBody = (log) => {
    const raw = String(streamPayload(log) ?? '');
    if (isPbFepSvg) {
      const lines = raw.length === 0 ? [''] : raw.split('\n');
      return (
        <div className="pb-fep-stream-panel">
          <span className="stream-data-chip">STREAM DATA</span>
          <div className="stream-lines" aria-label="스트림 데이터">
            {lines.map((line, i) => (
              <div key={`sl-${i}`} className="stream-line">
                {highlightKeywords(line, Array.isArray(keywords) ? keywords : [])}
              </div>
            ))}
          </div>
        </div>
      );
    }
    return (
      <pre className="tr-data-stream" aria-label="전문 데이터">
        {highlightKeywords(raw, Array.isArray(keywords) ? keywords : [])}
      </pre>
    );
  };

  return (
    <DataTable
      columns={columns}
      sortConfig={effectiveSortConfig}
      sortCriteria={sortCriteria != null && sortCriteria.length > 0 ? sortCriteria : null}
      onSort={onSort}
      loading={loading}
      emptyMessage="검색 결과가 없습니다."
      emptyColSpan={colCount}
      pagination={pagination}
      pageSize={pageSize}
      onPageSizeChange={onPageSizeChange}
      tableClassName={`${tableClassName} ${isPbFepSvg ? 'log-table--pb-fep-svg' : ''}`.trim()}
      containerClassName={dataTableContainerClassName}
      paginationFooterOrder={dataTablePaginationFooterOrder}
      ariaLabel="로그 검색 결과"
    >
      {logs.length === 0 ? (
        <EmptyTableBody colSpan={colCount} message="검색 결과가 없습니다." />
      ) : (
        logs.map((log) => {
          const rowKey = getPbFeplogRowKey(log);
          const isExpanded = expandedRows.has(rowKey);
          if (isPbFepSvg) {
            return (
              <React.Fragment key={rowKey}>
                <tr className="log-row-pb-fep-svg">
                  <td className="pb-fep-timestamp-cell">
                    <div className="pb-fep-timestamp-cell-inner">
                      <span className="pb-fep-expand-hint-inner" aria-hidden="true">
                        {isExpanded ? '▾' : '▸'}
                      </span>
                      <span className="pb-fep-timestamp-value">
                        {formatTime(log.log_timestamp || log.timestamp || log.prc_time || log.log_time)}
                      </span>
                    </div>
                  </td>
                  <td>{log.tr_code ?? ''}</td>
                  <td>{log.login_id ?? log.user_id ?? log.loginId ?? ''}</td>
                  <td>{log.msg_code ?? log.status_code ?? ''}</td>
                  <td>{log.bmsg ?? log.error_message ?? ''}</td>
                  <td>{log.log_ch_cd ?? log.device_type ?? ''}</td>
                  <td>{log.send_recv ?? log.log_type ?? ''}</td>
                  <td>{log.src_ip ?? log.ip_address ?? ''}</td>
                  <td>{log.dest_ip ?? ''}</td>
                  <td>{log.app_id ?? log.session_id ?? ''}</td>
                  <td className="tr-data-cell tr-data-cell--pb-fep-svg">
                    <button
                      type="button"
                      className="tr-data-expand-action"
                      onClick={() => toggleRowExpanded(rowKey)}
                      aria-expanded={isExpanded}
                      aria-label={isExpanded ? '전문 접기' : '전문 펼치기'}
                    >
                      {isExpanded ? '접기 ▴' : '전문보기 ▾'}
                    </button>
                  </td>
                </tr>
                {isExpanded ? (
                  <tr className="log-expand-stream-row">
                    <td colSpan={colCount} className="log-expand-stream-cell log-expand-stream-cell--svg">
                      {renderStreamBody(log)}
                    </td>
                  </tr>
                ) : null}
              </React.Fragment>
            );
          }
          return (
            <React.Fragment key={rowKey}>
              <tr>
                <td>{formatTime(log.log_timestamp || log.timestamp || log.prc_time || log.log_time)}</td>
                <td>{log.tr_code || log.trCode}</td>
                <td>{log.user_id || log.loginId || log.brodid}</td>
                <td>{log.status_code || log.msg_code}</td>
                <td>{log.error_message || log.bmsg}</td>
                <td>{log.device_type || log.log_ch_cd}</td>
                <td>{log.log_type || log.log_io_cd}</td>
                <td>{log.ip_address || log.pub_ip}</td>
                <td>{log.session_id || log.prt_ip}</td>
                <td>{log.response_time != null ? log.response_time : log.term_no}</td>
                <td className="tr-data-cell tr-data-cell--pb-fep">
                  <button
                    type="button"
                    className="tr-data-expand-action"
                    onClick={() => toggleRowExpanded(rowKey)}
                    aria-expanded={isExpanded}
                    aria-label={isExpanded ? '전문 접기' : '전문 펼치기'}
                  >
                    {isExpanded ? '접기 ▴' : '전문보기 ▾'}
                  </button>
                </td>
              </tr>
              {isExpanded ? (
                <tr className="log-expand-stream-row">
                  <td colSpan={colCount} className="log-expand-stream-cell">
                    {renderStreamBody(log)}
                  </td>
                </tr>
              ) : null}
            </React.Fragment>
          );
        })
      )}
    </DataTable>
  );
};

export default LogTable;
