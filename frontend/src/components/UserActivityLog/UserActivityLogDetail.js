import React from 'react';
import { getActivityActionTypeLabel } from '../../utils/activityActionTypeOptions';
import {
  getPermissionGroupOperationLabel,
  isPermissionGroupFamilyActionType,
} from '../../utils/permissionGroupActivityAudit';
import './UserActivityLog.css';

/** Legacy flat enricher keys (may coexist with permissionGroupAuditV1 during transition). */
const LEGACY_PG_ENRICHER_KEYS = new Set([
  'permissionGroupId',
  'permissionGroupCode',
  'targetUserId',
  'allowedScreenCount',
  'screenIds',
]);

/**
 * @param {unknown} raw
 * @returns {Record<string, unknown>}
 */
function normalizeActionDetail(raw) {
  if (raw == null) return {};
  if (typeof raw === 'string') {
    try {
      const p = JSON.parse(raw);
      return typeof p === 'object' && p !== null && !Array.isArray(p) ? p : {};
    } catch {
      return {};
    }
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) return { ...raw };
  return {};
}

/**
 * @param {unknown} v
 * @returns {string}
 */
function plainText(v) {
  if (v == null) return '-';
  if (typeof v === 'boolean') return v ? '예' : '아니오';
  if (typeof v === 'number' && Number.isFinite(v)) return String(v);
  return String(v);
}

/**
 * @param {unknown} snap
 * @returns {Record<string, unknown>|null}
 */
function asSnapshot(snap) {
  if (snap == null) return null;
  if (typeof snap !== 'object' || Array.isArray(snap)) return null;
  return snap;
}

/**
 * @param {unknown} row
 * @returns {Record<string, unknown>|null}
 */
function asScreenRow(row) {
  if (row == null || typeof row !== 'object' || Array.isArray(row)) return null;
  return row;
}

const UserActivityLogDetail = ({ log, onClose, actionTypeLabelMap = {} }) => {
  if (!log) {
    return null;
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

  const formatJSON = (obj) => {
    if (!obj) return '-';
    try {
      let parsed;
      if (typeof obj === 'string') {
        parsed = JSON.parse(obj);
      } else {
        parsed = obj;
      }
      const masked = maskSensitiveData(parsed);
      return JSON.stringify(masked, null, 2);
    } catch (e) {
      return maskSensitiveData(obj.toString());
    }
  };

  const maskSensitiveData = (data) => {
    if (!data) return data;

    if (typeof data === 'string') {
      let masked = data.replace(/"password"\s*:\s*"([^"]+)"/gi, '"password":"***"');
      masked = masked.replace(/"pwd"\s*:\s*"([^"]+)"/gi, '"pwd":"***"');
      masked = masked.replace(/"secret"\s*:\s*"([^"]+)"/gi, '"secret":"***"');
      masked = masked.replace(/"token"\s*:\s*"([^"]+)"/gi, '"token":"***"');
      masked = masked.replace(/(\d{6}-)\d{7}/g, '$1*******');
      masked = masked.replace(/(\d{4}-)\d{4}-\d{4}-(\d{4})/g, '$1****-****-$2');
      masked = masked.replace(/(\d{3}-)\d{4}(-\d{4})/g, '$1****$2');
      masked = masked.replace(/([a-zA-Z0-9])[a-zA-Z0-9]*@/g, '$1***@');
      return masked;
    }

    if (typeof data === 'object') {
      if (Array.isArray(data)) {
        return data.map((item) => maskSensitiveData(item));
      }

      const masked = {};
      for (const key in data) {
        if (Object.prototype.hasOwnProperty.call(data, key)) {
          const lowerKey = key.toLowerCase();
          if (
            lowerKey.includes('password') ||
            lowerKey.includes('pwd') ||
            lowerKey.includes('secret') ||
            lowerKey.includes('token')
          ) {
            masked[key] = '***';
          } else {
            masked[key] = maskSensitiveData(data[key]);
          }
        }
      }
      return masked;
    }

    return data;
  };

  const actionDetail = normalizeActionDetail(log.action_detail);
  const pgAudit = actionDetail.permissionGroupAuditV1;
  const isPgFamily = isPermissionGroupFamilyActionType(log.action_type);
  const showActionDetailSection = !!(log.action_detail || isPgFamily);

  const renderAllowedScreensTable = (title, screens) => {
    if (!Array.isArray(screens) || screens.length === 0) {
      return (
        <div className="pg-audit-subsection">
          <h5 className="pg-audit-subheading">{title}</h5>
          <p className="pg-audit-empty">없음</p>
        </div>
      );
    }
    return (
      <div className="pg-audit-subsection">
        <h5 className="pg-audit-subheading">{title}</h5>
        <table className="summary-table pg-audit-screen-table">
          <thead>
            <tr>
              <th scope="col">화면 ID</th>
              <th scope="col">범위</th>
              <th scope="col">읽기</th>
              <th scope="col">쓰기</th>
              <th scope="col">승인</th>
              <th scope="col">복호화</th>
            </tr>
          </thead>
          <tbody>
            {screens.map((raw, idx) => {
              const row = asScreenRow(raw);
              if (!row) {
                return (
                  <tr key={`bad-${idx}`}>
                    <td colSpan={6}>{plainText(JSON.stringify(raw))}</td>
                  </tr>
                );
              }
              return (
                <tr key={`${String(row.screenId)}-${idx}`}>
                  <td>{plainText(row.screenId)}</td>
                  <td>{plainText(row.scope ?? 'team')}</td>
                  <td>{row.read == null ? '-' : plainText(row.read)}</td>
                  <td>{row.write == null ? '-' : plainText(row.write)}</td>
                  <td>{row.approve == null ? '-' : plainText(row.approve)}</td>
                  <td>{row.decrypt == null ? '-' : plainText(row.decrypt)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  const renderSnapshotMetaDiff = (before, after) => {
    const b = asSnapshot(before);
    const a = asSnapshot(after);
    const fields = ['code', 'name', 'description', 'sortOrder'];
    const rows = fields.map((field) => ({
      field,
      beforeVal: b && Object.prototype.hasOwnProperty.call(b, field) ? b[field] : undefined,
      afterVal: a && Object.prototype.hasOwnProperty.call(a, field) ? a[field] : undefined,
    }));
    return (
      <table className="summary-table">
        <thead>
          <tr>
            <th scope="col">필드</th>
            <th scope="col">변경 전</th>
            <th scope="col">변경 후</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(({ field, beforeVal, afterVal }) => (
            <tr key={field}>
              <th scope="row">{field}</th>
              <td>{beforeVal === undefined ? '-' : plainText(beforeVal)}</td>
              <td>{afterVal === undefined ? '-' : plainText(afterVal)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderPermissionGroupAuditV1 = () => {
    if (!pgAudit || typeof pgAudit !== 'object' || Array.isArray(pgAudit)) {
      return null;
    }
    const op = pgAudit.operation;
    const before = pgAudit.before;
    const after = pgAudit.after;
    const truncated = pgAudit.allowedScreensTruncated === true;

    return (
      <div className="permission-group-audit-section">
        <h4>권한 그룹 감사</h4>
        {truncated && (
          <p className="pg-audit-truncated-note" role="status">
            허용 화면 목록이 저장 정책에 따라 일부만 표시될 수 있습니다.
          </p>
        )}
        <table className="summary-table">
          <tbody>
            <tr>
              <th scope="row">스키마 버전</th>
              <td>{plainText(pgAudit.schemaVersion)}</td>
            </tr>
            <tr>
              <th scope="row">작업</th>
              <td>{plainText(getPermissionGroupOperationLabel(op))}</td>
            </tr>
            <tr>
              <th scope="row">권한 그룹 ID</th>
              <td>{plainText(pgAudit.permissionGroupId)}</td>
            </tr>
            {pgAudit.permissionGroupCode != null && String(pgAudit.permissionGroupCode) !== '' && (
              <tr>
                <th scope="row">권한 그룹 코드</th>
                <td>{plainText(pgAudit.permissionGroupCode)}</td>
              </tr>
            )}
            {pgAudit.targetUserId != null && (
              <tr>
                <th scope="row">대상 사용자 ID</th>
                <td>{plainText(pgAudit.targetUserId)}</td>
              </tr>
            )}
            {pgAudit.changeReason != null && String(pgAudit.changeReason).trim() !== '' && (
              <tr>
                <th scope="row">변경 사유</th>
                <td>{plainText(pgAudit.changeReason)}</td>
              </tr>
            )}
          </tbody>
        </table>

        {(before != null || after != null) && (
          <div className="pg-audit-subsection">
            <h5 className="pg-audit-subheading">그룹 메타데이터</h5>
            {renderSnapshotMetaDiff(before, after)}
          </div>
        )}

        {(asSnapshot(before)?.allowedScreens != null || asSnapshot(after)?.allowedScreens != null) && (
          <>
            {renderAllowedScreensTable(
              '변경 전 허용 화면',
              /** @type {unknown[]} */ (asSnapshot(before)?.allowedScreens ?? []),
            )}
            {renderAllowedScreensTable(
              '변경 후 허용 화면',
              /** @type {unknown[]} */ (asSnapshot(after)?.allowedScreens ?? []),
            )}
          </>
        )}
      </div>
    );
  };

  const renderPgLegacyAndUnknownTopLevel = () => {
    const legacyRows = [];
    const unknown = {};
    for (const key of Object.keys(actionDetail)) {
      if (key === 'searchSummary' || key === 'requestParams') continue;
      if (key === 'permissionGroupAuditV1') continue;
      const val = actionDetail[key];
      if (LEGACY_PG_ENRICHER_KEYS.has(key)) {
        legacyRows.push(
          <tr key={key}>
            <th scope="row">{key}</th>
            <td>
              {typeof val === 'object' && val !== null ? (
                <pre className="json-content json-pretty pg-audit-fallback-pre">{formatJSON(val)}</pre>
              ) : (
                plainText(val)
              )}
            </td>
          </tr>,
        );
      } else {
        unknown[key] = val;
      }
    }
    const hasLegacy = legacyRows.length > 0;
    const hasUnknown = Object.keys(unknown).length > 0;
    const hasV1 = pgAudit && typeof pgAudit === 'object' && !Array.isArray(pgAudit);
    if (!hasLegacy && !hasUnknown && !hasV1) {
      return (
        <p className="pg-audit-empty">
          구조화된 감사 정보가 없습니다. 레거시 필드 또는 JSON을 확인하세요.
        </p>
      );
    }
    if (!hasLegacy && !hasUnknown && hasV1) {
      return null;
    }
    return (
      <>
        {hasLegacy && (
          <div className="permission-group-legacy-flat">
            <h4>추가 필드 (레거시 enricher)</h4>
            <table className="summary-table">
              <tbody>{legacyRows}</tbody>
            </table>
          </div>
        )}
        {hasUnknown && (
          <div className="action-detail-unknown-keys">
            <h4>기타 키 (JSON)</h4>
            <pre className="json-content json-pretty">{formatJSON(unknown)}</pre>
          </div>
        )}
      </>
    );
  };

  return (
    <div className="activity-log-detail-overlay" onClick={onClose}>
      <div className="activity-log-detail-modal" onClick={(e) => e.stopPropagation()}>
        <div className="activity-log-detail-header">
          <h2>활동 이력 상세</h2>
          <button type="button" className="close-button" onClick={onClose}>
            ×
          </button>
        </div>
        <div className="activity-log-detail-content">
          <div className="detail-section">
            <h3>기본 정보</h3>
            <table className="detail-table">
              <tbody>
                <tr>
                  <th>ID</th>
                  <td>{log.id}</td>
                </tr>
                <tr>
                  <th>사용자 ID</th>
                  <td>{log.user_id || '-'}</td>
                </tr>
                <tr>
                  <th>사용자명</th>
                  <td>{log.username || '-'}</td>
                </tr>
                <tr>
                  <th>액션 타입</th>
                  <td>{getActivityActionTypeLabel(log.action_type, actionTypeLabelMap)}</td>
                </tr>
                <tr>
                  <th>IP 주소</th>
                  <td>{log.ip_address || '-'}</td>
                </tr>
                <tr>
                  <th>User-Agent</th>
                  <td>{log.user_agent || '-'}</td>
                </tr>
                <tr>
                  <th>요청 메서드</th>
                  <td>{log.request_method || '-'}</td>
                </tr>
                <tr>
                  <th>요청 경로</th>
                  <td>{log.request_path || '-'}</td>
                </tr>
                <tr>
                  <th>응답 상태</th>
                  <td>{log.response_status || '-'}</td>
                </tr>
                <tr>
                  <th>응답 시간</th>
                  <td>
                    {log.response_time_ms != null ? `${log.response_time_ms}ms` : '-'}
                  </td>
                </tr>
                <tr>
                  <th>성공 여부</th>
                  <td>{log.success ? '성공' : '실패'}</td>
                </tr>
                <tr>
                  <th>에러 메시지</th>
                  <td>{log.error_message || '-'}</td>
                </tr>
                <tr>
                  <th>생성일시</th>
                  <td>{formatDateTime(log.created_at)}</td>
                </tr>
                <tr>
                  <th>수정일시</th>
                  <td>{formatDateTime(log.updated_at)}</td>
                </tr>
              </tbody>
            </table>
          </div>

          {showActionDetailSection && (
            <div className="detail-section">
              <h3>액션 상세</h3>
              {actionDetail.searchSummary && (
                <div className="search-summary">
                  <h4>검색 결과 요약</h4>
                  <table className="summary-table">
                    <tbody>
                      {actionDetail.searchSummary.totalCount != null && (
                        <tr>
                          <th>전체 건수</th>
                          <td>
                            {actionDetail.searchSummary.totalCount.toLocaleString()}건
                            <span className="summary-description">(검색 조건에 맞는 전체 결과)</span>
                          </td>
                        </tr>
                      )}
                      {actionDetail.searchSummary.resultCount != null && (
                        <tr>
                          <th>반환 건수</th>
                          <td>
                            {actionDetail.searchSummary.resultCount.toLocaleString()}건
                            <span className="summary-description">(현재 페이지에서 실제로 반환된 데이터)</span>
                          </td>
                        </tr>
                      )}
                      {actionDetail.searchSummary.currentPage != null && (
                        <tr>
                          <th>현재 페이지</th>
                          <td>
                            {actionDetail.searchSummary.currentPage}페이지
                            {actionDetail.searchSummary.totalPages != null &&
                              actionDetail.searchSummary.totalPages > 1 && (
                                <span className="summary-description">
                                  (전체 {actionDetail.searchSummary.totalPages}페이지 중)
                                </span>
                              )}
                          </td>
                        </tr>
                      )}
                      {actionDetail.searchSummary.totalPages != null && (
                        <tr>
                          <th>전체 페이지</th>
                          <td>
                            {actionDetail.searchSummary.totalPages}페이지
                            <span className="summary-description">
                              (페이지당 표시 가능한 최대 건수 기준)
                            </span>
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}
              {actionDetail.requestParams && (
                <div className="search-conditions">
                  <h4>검색 조건</h4>
                  {(() => {
                    const requestParams = actionDetail.requestParams;
                    let request = requestParams.request;

                    if (typeof request === 'string') {
                      try {
                        request = JSON.parse(request);
                      } catch (e) {
                        // ignore
                      }
                    }

                    if (!request && typeof requestParams === 'object') {
                      if (requestParams.logType || requestParams.startDate) {
                        request = requestParams;
                      }
                    }

                    if (request && typeof request === 'object' && !Array.isArray(request)) {
                      return (
                        <div className="search-conditions-detail">
                          <table className="summary-table">
                            <tbody>
                              {request.logType && (
                                <tr>
                                  <th>로그 타입</th>
                                  <td>{request.logType}</td>
                                </tr>
                              )}
                              {request.startDate && (
                                <tr>
                                  <th>시작 날짜</th>
                                  <td>{request.startDate}</td>
                                </tr>
                              )}
                              {request.endDate && (
                                <tr>
                                  <th>종료 날짜</th>
                                  <td>{request.endDate}</td>
                                </tr>
                              )}
                              {request.page && (
                                <tr>
                                  <th>페이지</th>
                                  <td>
                                    {request.page} /{' '}
                                    {request.pageSize ? `페이지당 ${request.pageSize}건` : ''}
                                  </td>
                                </tr>
                              )}
                              {request.logType === 'java_fw_imglog' && (
                                <>
                                  {request.application && (
                                    <tr>
                                      <th>Application</th>
                                      <td>{request.application}</td>
                                    </tr>
                                  )}
                                  {request.servicegroup && (
                                    <tr>
                                      <th>Service Group</th>
                                      <td>{request.servicegroup}</td>
                                    </tr>
                                  )}
                                  {request.service && (
                                    <tr>
                                      <th>Service</th>
                                      <td>{request.service}</td>
                                    </tr>
                                  )}
                                  {request.datastring && (
                                    <tr>
                                      <th>Data String</th>
                                      <td>{request.datastring}</td>
                                    </tr>
                                  )}
                                  {request.headerstring && (
                                    <tr>
                                      <th>Header String</th>
                                      <td>{request.headerstring}</td>
                                    </tr>
                                  )}
                                  {request.keywords &&
                                    Array.isArray(request.keywords) &&
                                    request.keywords.length > 0 && (
                                      <tr>
                                        <th>Keywords</th>
                                        <td>{request.keywords.join(', ')}</td>
                                      </tr>
                                    )}
                                </>
                              )}
                              {request.logType === 'pb_feplog' && (
                                <>
                                  {request.mediaCode && (
                                    <tr>
                                      <th>매체 코드</th>
                                      <td>{request.mediaCode}</td>
                                    </tr>
                                  )}
                                  {request.trCode && (
                                    <tr>
                                      <th>TR 코드</th>
                                      <td>{request.trCode}</td>
                                    </tr>
                                  )}
                                  {request.loginId && (
                                    <tr>
                                      <th>로그인 ID</th>
                                      <td>{request.loginId}</td>
                                    </tr>
                                  )}
                                </>
                              )}
                            </tbody>
                          </table>
                          <details className="json-details">
                            <summary>전체 JSON 보기</summary>
                            <pre className="json-content json-pretty">{formatJSON(requestParams)}</pre>
                          </details>
                        </div>
                      );
                    }
                    return <pre className="json-content json-pretty">{formatJSON(requestParams)}</pre>;
                  })()}
                </div>
              )}

              {isPgFamily && (
                <>
                  {renderPermissionGroupAuditV1()}
                  {renderPgLegacyAndUnknownTopLevel()}
                </>
              )}
            </div>
          )}

          {log.request_params && (
            <div className="detail-section">
              <h3>요청 파라미터</h3>
              <pre className="json-content">{formatJSON(log.request_params)}</pre>
            </div>
          )}
        </div>
        <div className="activity-log-detail-footer">
          <button type="button" className="btn btn-primary" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};

export default UserActivityLogDetail;
