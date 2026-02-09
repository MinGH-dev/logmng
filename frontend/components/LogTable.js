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

  // 날짜 포맷팅
  const formatDate = (dateString) => {
    try {
      return format(new Date(dateString), 'yyyy-MM-dd HH:mm:ss');
    } catch (error) {
      return dateString;
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
                onClick={() => onSort('timestamp')}
                className="sortable-header"
              >
                일시 {renderSortIcon('timestamp')}
              </th>
              <th 
                onClick={() => onSort('mediaCode')}
                className="sortable-header"
              >
                매체코드 {renderSortIcon('mediaCode')}
              </th>
              <th 
                onClick={() => onSort('trCode')}
                className="sortable-header"
              >
                TR Code {renderSortIcon('trCode')}
              </th>
              <th 
                onClick={() => onSort('loginId')}
                className="sortable-header"
              >
                Login ID {renderSortIcon('loginId')}
              </th>
              <th>TR Data</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr>
                <td colSpan="5" className="no-data">
                  검색 결과가 없습니다.
                </td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr key={log.id}>
                  <td>{formatDate(log.timestamp)}</td>
                  <td>{log.mediaCode}</td>
                  <td>{log.trCode}</td>
                  <td>{log.loginId}</td>
                  <td className="tr-data-cell">
                    <span className="tr-data-text">
                      {log.decryptedData ? 
                        highlightKeywords(log.decryptedData, keywords) :
                        log.trData
                      }
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