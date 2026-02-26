import React from 'react';
import './DataTable.css';

/**
 * Shared data table component. Single structure and sort contract per docs/design/grid-and-table.md
 * and docs/workflow/CONSISTENCY-STANDARDS.md §6.
 *
 * Props:
 * - columns: Array<{ key: string, label: React.ReactNode, sortable?: boolean }>
 * - sortConfig?: { key: string, direction: 'asc'|'desc' } | null
 * - onSort?: (key: string) => void
 * - loading?: boolean
 * - emptyMessage?: string
 * - emptyColSpan?: number
 * - children: React node(s) for tbody (typically array of <tr>)
 * - pagination?: { currentPage, totalPages, onPageChange, infoText? } | null
 * - tableClassName?: optional extra class on <table>
 * - ariaLabel?: string for table aria-label
 */
const DataTable = ({
  columns = [],
  sortConfig = null,
  onSort,
  loading = false,
  emptyMessage = '데이터가 없습니다.',
  emptyColSpan,
  children,
  pagination = null,
  tableClassName = '',
  ariaLabel,
}) => {
  const renderSortIcon = (key) => {
    if (!sortConfig || sortConfig.key !== key) {
      return <span className="sort-icon" aria-hidden>↕</span>;
    }
    return sortConfig.direction === 'asc'
      ? <span className="sort-icon" aria-hidden>↑</span>
      : <span className="sort-icon" aria-hidden>↓</span>;
  };

  const getAriaSort = (key) => {
    if (!sortConfig || sortConfig.key !== key) return 'none';
    return sortConfig.direction === 'asc' ? 'ascending' : 'descending';
  };

  const handleHeaderKeyDown = (e, key) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (onSort) onSort(key);
    }
  };

  const renderPaginationButtons = () => {
    if (!pagination || pagination.totalPages <= 1) return null;
    const { currentPage, totalPages, onPageChange, simple } = pagination;
    if (simple) {
      return (
        <>
          <button type="button" disabled={currentPage <= 1} onClick={() => onPageChange(currentPage - 1)} className="page-btn" aria-label="이전 페이지">이전</button>
          <span aria-live="polite">{currentPage} / {totalPages || 1}</span>
          <button type="button" disabled={currentPage >= totalPages} onClick={() => onPageChange(currentPage + 1)} className="page-btn" aria-label="다음 페이지">다음</button>
        </>
      );
    }
    const buttons = [];
    const maxVisiblePages = 5;
    let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);
    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }
    if (currentPage > 1) {
      buttons.push(
        <button key="prev" type="button" onClick={() => onPageChange(currentPage - 1)} className="page-btn">이전</button>
      );
    }
    for (let i = startPage; i <= endPage; i++) {
      buttons.push(
        <button key={i} type="button" onClick={() => onPageChange(i)} className={`page-btn ${i === currentPage ? 'active' : ''}`}>{i}</button>
      );
    }
    if (currentPage < totalPages) {
      buttons.push(
        <button key="next" type="button" onClick={() => onPageChange(currentPage + 1)} className="page-btn">다음</button>
      );
    }
    return buttons;
  };

  if (loading) {
    return (
      <div className="log-table-container">
        <div className="loading-container" aria-live="polite">
          <div className="loading-spinner" aria-hidden />
          <p>데이터를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="log-table-container">
      <div className="table-wrapper">
        <table className={`log-table ${tableClassName}`.trim()} aria-label={ariaLabel}>
          <thead>
            <tr>
              {columns.map((col) => {
                const sortable = col.sortable && onSort;
                if (sortable) {
                  return (
                    <th
                      key={col.key}
                      scope="col"
                      className="sortable-header"
                      aria-sort={getAriaSort(col.key)}
                      tabIndex={0}
                      onClick={() => onSort(col.key)}
                      onKeyDown={(e) => handleHeaderKeyDown(e, col.key)}
                    >
                      {col.label} {renderSortIcon(col.key)}
                    </th>
                  );
                }
                return (
                  <th key={col.key} scope="col">
                    {col.label}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {children}
          </tbody>
        </table>
      </div>
      {pagination && pagination.totalPages > 1 && (
        <div className="pagination">
          {pagination.infoText != null ? (
            <div className="pagination-info">{pagination.infoText}</div>
          ) : null}
          <div className={pagination.simple ? 'pagination-buttons pagination-simple' : 'pagination-buttons'}>
            {renderPaginationButtons()}
          </div>
        </div>
      )}
    </div>
  );
};

export default DataTable;

/** Helper: render single empty row for use as DataTable children when data length is 0. */
export const EmptyTableBody = ({ colSpan, message = '데이터가 없습니다.' }) => (
  <tr>
    <td colSpan={colSpan} className="no-data">
      {message}
    </td>
  </tr>
);
