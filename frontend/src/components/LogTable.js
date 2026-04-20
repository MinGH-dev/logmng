import React, { useState } from 'react';
import { format } from 'date-fns';
import DataTable, { EmptyTableBody } from './DataTable';
import { highlightKeywordsAsHtml, lineHasKeywordHighlightHtml } from '../utils/keywordHighlight';
import './LogTable.css';

/**
 * Coerce search keywords to a trimmed string[] (LogGrid may pass a comma string from some paths).
 * @param {unknown} kw
 * @returns {string[]}
 */
export function normalizeLogTableKeywords(kw) {
  if (kw == null || kw === '') return [];
  if (Array.isArray(kw)) {
    return kw.map((k) => String(k).trim()).filter(Boolean);
  }
  if (typeof kw === 'string') {
    return kw.split(',').map((k) => k.trim()).filter(Boolean);
  }
  return [];
}

/**
 * PB FEP `keyword_match_*` may arrive as JSON booleans or as strings ("true"/"1") from loose serializers.
 * Do not use arbitrary truthiness (e.g. non-empty string) — only known true representations.
 * @param {unknown} v
 * @returns {boolean}
 */
export function coerceKeywordMatchFlag(v) {
  if (v === true) return true;
  if (v === false) return false;
  if (v == null) return false;
  if (typeof v === 'string') {
    const t = v.trim().toLowerCase();
    return t === 'true' || t === '1';
  }
  if (typeof v === 'number' && v === 1) return true;
  return false;
}

/**
 * PB FEP `keyword_match_*` may arrive as snake_case from the API or camelCase if a gateway rewrites nested keys.
 * Use snake_case first when defined, then camelCase.
 * @param {unknown} log
 * @param {string} snakeKey
 * @param {string} camelKey
 * @returns {unknown}
 */
export function resolveKeywordMatchField(log, snakeKey, camelKey) {
  if (!log || typeof log !== 'object') return undefined;
  const fromSnake = log[snakeKey];
  if (fromSnake !== undefined) return fromSnake;
  return log[camelKey];
}

/**
 * ImageLog-style flags + PB FEP wireframe `keyword_match_*` (server decrypt/plaintext match) so
 * highlightKeywordsAsHtml can use encrypted heuristics when ciphertext has no literal keyword.
 */
function getPbFepOptionalEncryptedMatchHint(log) {
  if (!log || typeof log !== 'object') return false;
  if (
    log.hasEncryptedMatchData ||
    log.hasEncryptedMatchDatastring ||
    log.hasEncryptedMatchHeader ||
    log.hasEncryptedMatchHeaderstring ||
    log._datastring_has_encrypted_match
  ) {
    return true;
  }
  return (
    coerceKeywordMatchFlag(
      resolveKeywordMatchField(log, 'keyword_match_request_data', 'keywordMatchRequestData')
    ) ||
    coerceKeywordMatchFlag(
      resolveKeywordMatchField(log, 'keyword_match_response_data', 'keywordMatchResponseData')
    ) ||
    coerceKeywordMatchFlag(resolveKeywordMatchField(log, 'keyword_match_data', 'keywordMatchData')) ||
    coerceKeywordMatchFlag(resolveKeywordMatchField(log, 'keyword_match_bmsg', 'keywordMatchBmsg'))
  );
}

function pbFepSvgPayloadStringNonEmpty(value) {
  return value != null && String(value).trim() !== '';
}

/** Wireframe rows may be snake_case (Jackson Map) or camelCase (proxies); read both. */
function pbFepWireframeRequestData(log) {
  if (!log || typeof log !== 'object') return '';
  const v = log.request_data ?? log.requestData;
  return v == null ? '' : v;
}

function pbFepWireframeResponseData(log) {
  if (!log || typeof log !== 'object') return '';
  const v = log.response_data ?? log.responseData;
  return v == null ? '' : v;
}

function pbFepWireframeBmsg(log) {
  if (!log || typeof log !== 'object') return '';
  const v = log.bmsg ?? log.error_message ?? log.Bmsg ?? log.errorMessage;
  return v == null ? '' : v;
}

/**
 * PB FEP SVG stream body — align with backend wireframe preview priority: full `response_data`, else full
 * `request_data`, else `bmsg`, else `data` (200-char summary / legacy rows). API may camelCase keys; bmsg
 * still maps from error_message for parity with row filter.
 */
function pbFepSvgStreamPayloadRaw(log) {
  if (!log || typeof log !== 'object') return '';
  const res = pbFepWireframeResponseData(log);
  if (pbFepSvgPayloadStringNonEmpty(res)) return res;
  const req = pbFepWireframeRequestData(log);
  if (pbFepSvgPayloadStringNonEmpty(req)) return req;
  const bmsg = pbFepWireframeBmsg(log);
  if (pbFepSvgPayloadStringNonEmpty(bmsg)) return bmsg;
  if (pbFepSvgPayloadStringNonEmpty(log.data)) return log.data;
  return '';
}

/**
 * Which physical column family drives the displayed stream (align with `pbFepSvgStreamPayloadRaw`).
 */
function pbFepSvgStreamSourceKey(log) {
  if (!log || typeof log !== 'object') return null;
  if (pbFepSvgPayloadStringNonEmpty(pbFepWireframeResponseData(log))) return 'response_data';
  if (pbFepSvgPayloadStringNonEmpty(pbFepWireframeRequestData(log))) return 'request_data';
  if (pbFepSvgPayloadStringNonEmpty(pbFepWireframeBmsg(log))) return 'bmsg';
  if (pbFepSvgPayloadStringNonEmpty(log.data)) return 'data';
  return null;
}

/**
 * Legacy {@code POST .../search} row stream body: {@code request_data || response_data || data || ...}
 * (same order as non-wireframe {@code streamPayload} in this component).
 */
function pbFepLegacyStreamSourceKey(log) {
  if (!log || typeof log !== 'object') return null;
  if (log.request_data || log.requestData) return 'request_data';
  if (log.response_data || log.responseData) return 'response_data';
  if (log.data) return 'data';
  if (log.trData) return 'trData';
  if (log.decryptedData) return 'decryptedData';
  return null;
}

/**
 * Mirrors server {@code keyword_match_*} for a resolved stream column (wireframe or legacy order).
 * For {@code data} stream source (summary): if {@code keyword_match_data} is set, treat as aggregate OR with other
 * column flags; otherwise rely on {@code keyword_match_bmsg} only.
 */
function pbFepStreamKeywordMatchFlagForSource(log, src) {
  if (!log || typeof log !== 'object' || !src) return false;
  const kmData = resolveKeywordMatchField(log, 'keyword_match_data', 'keywordMatchData');
  const kmReq = resolveKeywordMatchField(log, 'keyword_match_request_data', 'keywordMatchRequestData');
  const kmRes = resolveKeywordMatchField(log, 'keyword_match_response_data', 'keywordMatchResponseData');
  const mReq = coerceKeywordMatchFlag(kmReq);
  const mRes = coerceKeywordMatchFlag(kmRes);
  const mBmsg = coerceKeywordMatchFlag(
    resolveKeywordMatchField(log, 'keyword_match_bmsg', 'keywordMatchBmsg')
  );
  if (src === 'data') {
    if (kmData !== undefined && kmData !== null) {
      const mData = coerceKeywordMatchFlag(kmData);
      return Boolean(mData || mRes || mReq || mBmsg);
    }
    return mBmsg;
  }
  if (src === 'request_data') {
    return mReq || mBmsg;
  }
  if (src === 'response_data') {
    return mRes || mBmsg;
  }
  if (src === 'bmsg') {
    return mBmsg;
  }
  return false;
}

/** Legacy POST .../search columns + sort keys (pb-feplog). */
const LOG_COLUMNS_PB_FEP_LEGACY = [
  { key: 'log_time', label: 'log_time', sortable: true },
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

/** Wireframe SVG v10 — POST .../pb-fep-log-search row keys (expand chevron in log_time cell). */
const LOG_COLUMNS_PB_FEP_SVG = [
  { key: 'log_time', label: 'log_time', sortable: true },
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
  const canonicalTime = log.log_time ?? '';
  const id =
    log.id != null
      ? String(log.id)
      : `${canonicalTime}-${log.tr_code}-${log.user_id ?? log.login_id ?? ''}`;
  return `${lt}-${id}`;
}

/**
 * PB FEP / grid timestamp display. Handles compact lexical `log_time`:
 * - 20 digits: `yyyyMMddHHmmssSSSSSS` (microseconds)
 * - 14 digits: legacy second-only `yyyyMMddHHmmss`
 * @param {string|number|undefined|null} timeString
 * @returns {string}
 */
export function formatLogTableTime(timeString) {
  if (!timeString) return '';
  if (typeof timeString === 'string' && timeString.length === 9 && /^\d{9}$/.test(timeString)) {
    const hours = timeString.substring(0, 2);
    const minutes = timeString.substring(2, 4);
    const seconds = timeString.substring(4, 6);
    const milliseconds = timeString.substring(6, 9);
    return `${hours}:${minutes}:${seconds}.${milliseconds}`;
  }
  if (typeof timeString === 'string' && /^\d{20}$/.test(timeString)) {
    const y = timeString.slice(0, 4);
    const mo = timeString.slice(4, 6);
    const d = timeString.slice(6, 8);
    const h = timeString.slice(8, 10);
    const mi = timeString.slice(10, 12);
    const s = timeString.slice(12, 14);
    const micro = timeString.slice(14, 20);
    return `${y}-${mo}-${d} ${h}:${mi}:${s}.${micro}`;
  }
  if (typeof timeString === 'string' && /^\d{14}$/.test(timeString)) {
    const y = timeString.slice(0, 4);
    const mo = timeString.slice(4, 6);
    const d = timeString.slice(6, 8);
    const h = timeString.slice(8, 10);
    const mi = timeString.slice(10, 12);
    const s = timeString.slice(12, 14);
    return `${y}-${mo}-${d} ${h}:${mi}:${s}`;
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

  const kwList = normalizeLogTableKeywords(keywords);

  const renderHighlightedText = (displayValue, log) => {
    const text = displayValue == null ? '' : String(displayValue);
    if (kwList.length === 0) {
      return text;
    }
    const hasEncHint = getPbFepOptionalEncryptedMatchHint(log);
    return (
      <span
        dangerouslySetInnerHTML={{
          __html: highlightKeywordsAsHtml(text, kwList, null, hasEncHint, null, false),
        }}
      />
    );
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
      return pbFepSvgStreamPayloadRaw(log);
    }
    return log.request_data || log.response_data || log.data || log.trData || log.decryptedData || '';
  };

  const renderStreamBody = (log) => {
    const raw = String(streamPayload(log) ?? '');
    const lines = raw.length === 0 ? [''] : raw.split('\n');
    const hasEncHint = getPbFepOptionalEncryptedMatchHint(log);
    const streamHtml = (chunk) =>
      kwList.length === 0
        ? chunk
        : highlightKeywordsAsHtml(chunk, kwList, null, hasEncHint, null, false);
    const lineHasHtmlHit = (line) =>
      kwList.length > 0 &&
      lineHasKeywordHighlightHtml(line, kwList, null, hasEncHint, null, false);
    const anyLineHasHtmlHit = lines.some((line) => lineHasHtmlHit(line));
    const streamSourceKey = isPbFepSvg ? pbFepSvgStreamSourceKey(log) : pbFepLegacyStreamSourceKey(log);
    const decryptOnlyStreamBulkHint =
      kwList.length > 0 &&
      streamSourceKey != null &&
      pbFepStreamKeywordMatchFlagForSource(log, streamSourceKey) &&
      !anyLineHasHtmlHit;
    const lineHit = (line) => lineHasHtmlHit(line) || decryptOnlyStreamBulkHint;

    const streamLineNodes = lines.map((line, i) => {
      const hit = lineHit(line);
      const lineClass = `stream-line${hit ? ' stream-line--keyword-hit' : ''}`;
      if (kwList.length === 0) {
        return (
          <div key={`sl-${i}`} className={lineClass}>
            {line}
          </div>
        );
      }
      return (
        <div
          key={`sl-${i}`}
          className={lineClass}
          dangerouslySetInnerHTML={{ __html: streamHtml(line) }}
        />
      );
    });

    if (isPbFepSvg) {
      return (
        <div className="pb-fep-stream-panel">
          <span className="stream-data-chip">STREAM DATA</span>
          <div className="stream-lines" aria-label="스트림 데이터">
            {streamLineNodes}
          </div>
        </div>
      );
    }
    return (
      <div className="stream-lines tr-data-stream" aria-label="전문 데이터">
        {streamLineNodes}
      </div>
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
          const bmsgText = log.bmsg ?? log.error_message ?? '';
          const bmsgEncHint = getPbFepOptionalEncryptedMatchHint(log);
          const bmsgDecryptOnlyFullLine =
            isPbFepSvg &&
            kwList.length > 0 &&
            coerceKeywordMatchFlag(
              resolveKeywordMatchField(log, 'keyword_match_bmsg', 'keywordMatchBmsg')
            ) &&
            !lineHasKeywordHighlightHtml(String(bmsgText), kwList, null, bmsgEncHint, null, false);
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
                        {renderHighlightedText(
                          formatLogTableTime(log.log_time || log.timestamp || log.prc_time),
                          log
                        )}
                      </span>
                    </div>
                  </td>
                  <td>{renderHighlightedText(log.tr_code ?? '', log)}</td>
                  <td>{renderHighlightedText(log.login_id ?? log.user_id ?? log.loginId ?? '', log)}</td>
                  <td>{renderHighlightedText(log.msg_code ?? log.status_code ?? '', log)}</td>
                  <td className={bmsgDecryptOnlyFullLine ? 'pb-fep-bmsg--keyword-hit' : undefined}>
                    {renderHighlightedText(bmsgText, log)}
                  </td>
                  <td>{renderHighlightedText(log.log_ch_cd ?? log.device_type ?? '', log)}</td>
                  <td>{renderHighlightedText(log.send_recv ?? log.log_type ?? '', log)}</td>
                  <td>{renderHighlightedText(log.src_ip ?? log.ip_address ?? '', log)}</td>
                  <td>{renderHighlightedText(log.dest_ip ?? '', log)}</td>
                  <td>{renderHighlightedText(log.app_id ?? log.session_id ?? '', log)}</td>
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
                <td>
                  {renderHighlightedText(
                    formatLogTableTime(log.log_time || log.timestamp || log.prc_time),
                    log
                  )}
                </td>
                <td>{renderHighlightedText(log.tr_code || log.trCode, log)}</td>
                <td>{renderHighlightedText(log.user_id || log.loginId || log.brodid, log)}</td>
                <td>{renderHighlightedText(log.status_code || log.msg_code, log)}</td>
                <td>{renderHighlightedText(log.error_message || log.bmsg, log)}</td>
                <td>{renderHighlightedText(log.device_type || log.log_ch_cd, log)}</td>
                <td>{renderHighlightedText(log.log_type || log.log_io_cd, log)}</td>
                <td>{renderHighlightedText(log.ip_address || log.pub_ip, log)}</td>
                <td>{renderHighlightedText(log.session_id || log.prt_ip, log)}</td>
                <td>
                  {renderHighlightedText(
                    log.response_time != null ? log.response_time : log.term_no,
                    log
                  )}
                </td>
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
