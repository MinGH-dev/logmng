import React from 'react';
import { format } from 'date-fns';
import './LogTable.css';

const LogTable = ({ 
  logs, 
  loading, 
  sortField, 
  sortDirection, 
  onSort, 
  currentPage, 
  totalPages, 
  onPageChange,
  keywords = []
}) => {
  // 정렬 아이콘 렌더링
  const renderSortIcon = (field) => {
    if (sortField !== field) {
      return <span className="sort-icon">↕</span>;
    }
    return sortDirection === 'asc' ? 
      <span className="sort-icon">↑</span> : 
      <span className="sort-icon">↓</span>;
  };

  // 시간 포맷팅 (24시간 형식, 초단위까지 표시)
  const formatTime = (timeString) => {
    if (!timeString) return '';
    
    // HH24MISSMS3 형식 (9자리) 처리
    if (typeof timeString === 'string' && timeString.length === 9 && /^\d{9}$/.test(timeString)) {
      const hours = timeString.substring(0, 2);
      const minutes = timeString.substring(2, 4);
      const seconds = timeString.substring(4, 6);
      const milliseconds = timeString.substring(6, 9);
      return `${hours}:${minutes}:${seconds}.${milliseconds}`;
    }
    
    // datetime-local 형식 처리 (YYYY-MM-DDTHH:mm:ss)
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
    
    // 기존 날짜 형식 처리
    try {
      return format(new Date(timeString), 'yyyy-MM-dd HH:mm:ss');
    } catch (error) {
      return timeString;
    }
  };

  // 키워드 하이라이트 함수
  const highlightKeywords = (text, keywords) => {
    if (!keywords || keywords.length === 0 || typeof text !== 'string') {
      return text;
    }

    // 첫 번째 키워드로만 하이라이트 처리 (단순화)
    const keyword = keywords[0];
    if (keyword && keyword.trim() !== '') {
      const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
      const parts = text.split(regex);
      
      return parts.map((part, index) => {
        if (regex.test(part)) {
          return <span key={`keyword-${index}`} className="highlight-keyword">{part}</span>;
        }
        return part;
      });
    }

    return text;
  };

  // 페이지네이션 버튼 생성
  const renderPaginationButtons = () => {
    const buttons = [];
    const maxVisiblePages = 5;
    
    let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);
    
    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    // 이전 페이지 버튼
    if (currentPage > 1) {
      buttons.push(
        <button
          key="prev"
          onClick={() => onPageChange(currentPage - 1)}
          className="page-btn"
        >
          이전
        </button>
      );
    }

    // 페이지 번호 버튼들
    for (let i = startPage; i <= endPage; i++) {
      buttons.push(
        <button
          key={i}
          onClick={() => onPageChange(i)}
          className={`page-btn ${i === currentPage ? 'active' : ''}`}
        >
          {i}
        </button>
      );
    }

    // 다음 페이지 버튼
    if (currentPage < totalPages) {
      buttons.push(
        <button
          key="next"
          onClick={() => onPageChange(currentPage + 1)}
          className="page-btn"
        >
          다음
        </button>
      );
    }

    return buttons;
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>데이터를 불러오는 중...</p>
      </div>
    );
  }

  return (
    <div className="log-table-container">
      <div className="table-wrapper">
        <table className="log-table">
          <thead>
            <tr>
              <th 
                onClick={() => onSort('log_timestamp')}
                className="sortable-header"
              >
                log_timestamp {renderSortIcon('log_timestamp')}
              </th>
              <th 
                onClick={() => onSort('media_code')}
                className="sortable-header"
              >
                media_code {renderSortIcon('media_code')}
              </th>
              <th 
                onClick={() => onSort('tr_code')}
                className="sortable-header"
              >
                tr_code {renderSortIcon('tr_code')}
              </th>
              <th 
                onClick={() => onSort('brodid')}
                className="sortable-header"
              >
                brodid {renderSortIcon('brodid')}
              </th>
              <th 
                onClick={() => onSort('msg_code')}
                className="sortable-header"
              >
                msg_code {renderSortIcon('msg_code')}
              </th>
              <th 
                onClick={() => onSort('bmsg')}
                className="sortable-header"
              >
                bmsg {renderSortIcon('bmsg')}
              </th>
              <th 
                onClick={() => onSort('log_ch_cd')}
                className="sortable-header"
              >
                log_ch_cd {renderSortIcon('log_ch_cd')}
              </th>
              <th 
                onClick={() => onSort('log_io_cd')}
                className="sortable-header"
              >
                log_io_cd {renderSortIcon('log_io_cd')}
              </th>
              <th 
                onClick={() => onSort('pub_ip')}
                className="sortable-header"
              >
                pub_ip {renderSortIcon('pub_ip')}
              </th>
              <th 
                onClick={() => onSort('prt_ip')}
                className="sortable-header"
              >
                prt_ip {renderSortIcon('prt_ip')}
              </th>
              <th 
                onClick={() => onSort('term_no')}
                className="sortable-header"
              >
                term_no {renderSortIcon('term_no')}
              </th>
              <th>data</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr>
                <td colSpan="12" className="no-data">
                  검색 결과가 없습니다.
                </td>
              </tr>
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
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="pagination">
          <div className="pagination-info">
            총 {logs.length}건의 데이터가 있습니다.
          </div>
          <div className="pagination-buttons">
            {renderPaginationButtons()}
          </div>
        </div>
      )}
    </div>
  );
};

export default LogTable; 