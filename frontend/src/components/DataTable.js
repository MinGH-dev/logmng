import React from 'react';
import './DataTable.css';

const PAGE_SIZE_MIN = 1;
const PAGE_SIZE_MAX = 100;
const DEFAULT_PAGE_SIZE_OPTIONS = [25, 50, 100];

/**
 * Shared data table component. Single structure and sort contract per docs/design/grid-and-table.md
 * and docs/workflow/CONSISTENCY-STANDARDS.md §6.
 *
 * Props:
 * - columns: Array<{ key: string, label: React.ReactNode, sortable?: boolean }>
 * - sortConfig?: { key: string, direction: 'asc'|'desc' } | null
 * - sortCriteria?: Array<{ key: string, direction: 'asc'|'desc' }> — multi-column header state (takes precedence over sortConfig for icons)
 * - onSort?: (key: string) => void
 * - containerClassName?: extra class on outer .log-table-container
 * - paginationFooterOrder?: 'default' | 'info-buttons-size' — PB FEP: center page buttons between info and rows-per-page
 * - loading?: boolean
 * - emptyMessage?: string
 * - emptyColSpan?: number
 * - children: React node(s) for tbody (typically array of <tr>)
 * - pagination?: { currentPage, totalPages, onPageChange, infoText?, simple? } | null
 * - pageSize?: number (rows per page; default 20 when rows-per-page control is used)
 * - onPageSizeChange?: (newSize: number) => void (optional; when set, shows +/- and Enter control)
 * - pageSizeMin?: number (default 1)
 * - pageSizeMax?: number (default 100)
 * - tableClassName?: optional extra class on <table>
 * - ariaLabel?: string for table aria-label
 */
const DataTable = ({
  columns = [],
  sortConfig = null,
  sortCriteria = null,
  onSort,
  loading = false,
  emptyMessage = '데이터가 없습니다.',
  emptyColSpan,
  children,
  pagination = null,
  pageSize: pageSizeProp = 20,
  onPageSizeChange,
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  pageSizeMin = PAGE_SIZE_MIN,
  pageSizeMax = PAGE_SIZE_MAX,
  tableClassName = '',
  containerClassName = '',
  paginationFooterOrder = 'default',
  ariaLabel,
}) => {
  const effectivePageSize = Math.max(pageSizeMin, Math.min(pageSizeMax, pageSizeProp));
  const showRowsPerPage = pagination != null && typeof onPageSizeChange === 'function';
  const showFooter = pagination != null;
  const normalizedPageSizeOptions = Array.from(new Set(pageSizeOptions
    .filter((v) => Number.isInteger(v) && v >= pageSizeMin && v <= pageSizeMax)
    .concat(effectivePageSize)))
    .sort((a, b) => a - b);
  const directionForKey = (key) => {
    if (sortCriteria != null && sortCriteria.length > 0) {
      const hit = sortCriteria.find((c) => c.key === key);
      return hit ? hit.direction : null;
    }
    if (sortConfig && sortConfig.key === key) return sortConfig.direction;
    return null;
  };

  const renderSortIcon = (key) => {
    const dir = directionForKey(key);
    if (!dir) {
      return <span className="sort-icon" aria-hidden>↕</span>;
    }
    return dir === 'asc'
      ? <span className="sort-icon" aria-hidden>↑</span>
      : <span className="sort-icon" aria-hidden>↓</span>;
  };

  const getAriaSort = (key) => {
    const dir = directionForKey(key);
    if (!dir) return 'none';
    return dir === 'asc' ? 'ascending' : 'descending';
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
          <span aria-live="polite" aria-label={`페이지 ${currentPage}, 총 ${totalPages || 1}페이지`}>{currentPage} / {totalPages || 1}</span>
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

  const outerClass = `log-table-container ${containerClassName}`.trim();

  if (loading) {
    return (
      <div className={outerClass}>
        <div className="loading-container" aria-live="polite">
          <div className="loading-spinner" aria-hidden />
          <p>데이터를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  const paginationButtons = renderPaginationButtons();

  const paginationInner = (
    <>
      {pagination.infoText != null ? (
        <div className="pagination-info">{pagination.infoText}</div>
      ) : null}
      {!pagination.simple && (
        <span className="pagination-aria" aria-live="polite" aria-atomic="true">
          페이지 {pagination.currentPage} / {pagination.totalPages || 1}
        </span>
      )}
    </>
  );

  const paginationSize = showRowsPerPage && (
    <div className="rows-per-page" aria-label="페이지당 행 수">
      <span className="rows-per-page-label">표시 건수</span>
      <select
        value={effectivePageSize}
        onChange={(e) => onPageSizeChange(parseInt(e.target.value, 10))}
        className="page-size-select"
        aria-label="페이지당 행 수"
      >
        {normalizedPageSizeOptions.map((size) => (
          <option key={size} value={size}>{size}건</option>
        ))}
      </select>
    </div>
  );

  const paginationNav = paginationButtons ? (
    <div className={pagination.simple ? 'pagination-buttons pagination-simple' : 'pagination-buttons'}>
      {paginationButtons}
    </div>
  ) : null;

  return (
    <div className={outerClass}>
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
      {showFooter && (
        <div
          className={`pagination ${paginationFooterOrder === 'info-buttons-size' ? 'pagination--info-buttons-size' : ''}`.trim()}
          role="navigation"
          aria-label="테이블 푸터"
          aria-live="polite"
          aria-atomic="true"
        >
          {paginationFooterOrder === 'info-buttons-size' ? (
            <>
              {paginationInner}
              {paginationNav}
              {paginationSize}
            </>
          ) : (
            <>
              {paginationInner}
              {paginationSize}
              {paginationNav}
            </>
          )}
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
