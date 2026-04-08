import React, { useState, useCallback } from 'react';
import { getActivityActionTypeLabel } from '../../utils/activityActionTypeOptions';
import {
  getPermissionGroupOperationLabel,
  isPermissionGroupFamilyActionType,
} from '../../utils/permissionGroupActivityAudit';
import { postActivityLogPrivilegedReveal } from '../../services/userActivityLogService';
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
 * `action_detail` keys handled elsewhere (dedicated sections / mutation summaries / blobs).
 * @type {Set<string>}
 */
const ACTION_DETAIL_STRUCTURAL_KEYS = new Set([
  'permissionGroupAuditV1',
  'department_admin',
  'searchSummary',
  'requestParams',
  'copyPayload',
  'before',
  'after',
  'deleteSnapshot',
  'deletedSnapshot',
  'snapshotBeforeDelete',
  'insertPayload',
]);

/**
 * Human-readable labels for `action_detail` fields (User Management v2 / `department_admin` / common audit).
 * Keys include snake_case variants for older payloads.
 * @type {Record<string, string>}
 */
const ACTION_DETAIL_FIELD_LABELS = {
  changeReason: '변경 사유',
  change_reason: '변경 사유',
  departmentCode: '부서 코드',
  department_code: '부서 코드',
  parentDepartmentId: '상위 부서 ID',
  parent_department_id: '상위 부서 ID',
  parentDepartmentCode: '상위 부서 코드',
  parent_department_code: '상위 부서 코드',
  targetUserId: '대상 사용자 ID (앱)',
  target_user_id: '대상 사용자 ID (앱)',
  userId: '사용자 ID (상세)',
  user_id: '사용자 ID (상세)',
  employeeNumber: '사번',
  employee_number: '사번',
  username: '로그인 사용자명',
  name: '이름',
  sortOrder: '정렬 순서',
  sort_order: '정렬 순서',
  operation: '작업 구분',
  source: '출처',
  registrationSource: '등록 출처',
  registration_source: '등록 출처',
  schemaVersion: '스키마 버전',
  schema_version: '스키마 버전',
};

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

function isPlainObject(v) {
  return v != null && typeof v === 'object' && !Array.isArray(v);
}

/**
 * Parse `request_params` like `action_detail` (string JSON or object).
 * @param {unknown} raw
 * @returns {unknown|null}
 */
function normalizeRequestParamsForDisplay(raw) {
  if (raw == null) return null;
  if (typeof raw === 'string') {
    const t = raw.trim();
    if (t === '') return null;
    try {
      return JSON.parse(raw);
    } catch {
      return raw;
    }
  }
  return raw;
}

/**
 * @param {unknown} raw
 * @returns {unknown|null}
 */
function normalizeSnapshotPayload(raw) {
  if (raw == null) return null;
  if (typeof raw === 'string') {
    const t = raw.trim();
    if (t === '') return null;
    try {
      return JSON.parse(raw);
    } catch {
      return raw;
    }
  }
  return raw;
}

/**
 * UPDATE/DELETE/INSERT summary from `action_detail` (not permissionGroupAuditV1).
 * @param {Record<string, unknown>} actionDetail
 * @returns {{ kind: 'UPDATE', before: object, after: object } | { kind: 'DELETE', snapshot: unknown, title: string } | { kind: 'INSERT', payload: unknown, title: string } | null}
 */
function getGenericMutationSummary(actionDetail) {
  if (!actionDetail || typeof actionDetail !== 'object') return null;
  if (actionDetail.permissionGroupAuditV1) return null;

  const delNamed =
    actionDetail.deleteSnapshot ??
    actionDetail.deletedSnapshot ??
    actionDetail.snapshotBeforeDelete;
  if (delNamed != null && delNamed !== '') {
    return { kind: 'DELETE', snapshot: delNamed, title: '삭제 직전 스냅샷' };
  }

  if (actionDetail.insertPayload != null && actionDetail.insertPayload !== '') {
    return { kind: 'INSERT', payload: actionDetail.insertPayload, title: '추가된 내용' };
  }

  const b = actionDetail.before;
  const a = actionDetail.after;
  const bObj = isPlainObject(b);
  const aObj = isPlainObject(a);

  if (bObj && aObj) {
    return { kind: 'UPDATE', before: b, after: a };
  }
  if (bObj && !aObj) {
    return { kind: 'DELETE', snapshot: b, title: '삭제 직전 스냅샷' };
  }
  if (aObj && !bObj) {
    return { kind: 'INSERT', payload: a, title: '추가된 내용' };
  }

  return null;
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

/** @param {string|undefined} actionType */
function isInAppCopyActionType(actionType) {
  if (!actionType || typeof actionType !== 'string') return false;
  const u = actionType.trim().toUpperCase();
  return u === 'IN_APP_COPY';
}

/** USER_CREATE / USER_DELETE flat `action_detail` (docs/api-definition.md §8.0.1) */
function isUserLifecycleActionType(actionType) {
  if (!actionType || typeof actionType !== 'string') return false;
  const u = actionType.trim().toUpperCase();
  return u === 'USER_CREATE' || u === 'USER_DELETE';
}

/** @param {string|undefined} actionType */
function getUserLifecycleDetailExcludedKeys(actionType) {
  if (!isUserLifecycleActionType(actionType)) return new Set();
  return new Set([
    'changeReason',
    'change_reason',
    'targetUserId',
    'target_user_id',
    'employeeNumber',
    'employee_number',
    'username',
    'departmentCode',
    'department_code',
    'name',
    'source',
    'registrationSource',
    'registration_source',
  ]);
}

/**
 * @param {Record<string, unknown>} actionDetail
 * @returns {Record<string, unknown>|null}
 */
function getCopyPayload(actionDetail) {
  const cp = actionDetail?.copyPayload;
  if (cp && typeof cp === 'object' && !Array.isArray(cp)) return cp;
  return null;
}

/**
 * @param {Record<string, unknown>} log
 * @param {Record<string, unknown>|null} copyPayload
 */
function canOfferPrivilegedCopyReveal(log, copyPayload) {
  if (!copyPayload) return false;
  const truncated =
    copyPayload.was_truncated === true ||
    copyPayload.wasTruncated === true;
  if (!truncated) return false;
  const allowed =
    log.privilegedRevealCopyBodyAllowed === true ||
    log.privileged_reveal_copy_body_allowed === true ||
    copyPayload.privilegedRevealAllowed === true ||
    copyPayload.privileged_reveal_allowed === true;
  return allowed === true;
}

const UserActivityLogDetail = ({
  log,
  onClose,
  actionTypeLabelMap = {},
  loading = false,
  error = null,
  onNavigateToAccessAudit,
  canOpenAccessAudit = false,
  accessAuditState = null,
}) => {
  const [revealedCopyBody, setRevealedCopyBody] = useState(null);
  const [revealLoading, setRevealLoading] = useState(false);
  const [revealError, setRevealError] = useState(null);

  const handleRevealFullCopy = useCallback(async () => {
    if (!log?.id) return;
    setRevealError(null);
    setRevealLoading(true);
    try {
      const res = await postActivityLogPrivilegedReveal(log.id, 'COPY_BODY_FULL');
      if (res.success && res.data) {
        const full =
          res.data.copyBodyFull ??
          res.data.copy_body_full ??
          (typeof res.data === 'string' ? res.data : null);
        if (full != null) {
          setRevealedCopyBody(String(full));
        } else {
          setRevealError('응답에 전체 본문이 없습니다.');
        }
      } else if (res.status === 403 || res.code === 'REVEAL_NOT_ALLOWED' || res.code === 'FUNCTION_NOT_ALLOWED') {
        setRevealError('전체 복사 본문을 볼 권한이 없습니다.');
      } else {
        setRevealError(res.error || res.message || '특권 공개 요청에 실패했습니다.');
      }
    } catch (e) {
      setRevealError(e?.message || '특권 공개 요청에 실패했습니다.');
    } finally {
      setRevealLoading(false);
    }
  }, [log?.id]);

  if (!log && !loading && !error) {
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

  const actionDetail = normalizeActionDetail(log?.action_detail);
  const pgAudit = actionDetail.permissionGroupAuditV1;
  const isPgFamily = log ? isPermissionGroupFamilyActionType(log.action_type) : false;
  const showActionDetailSection = !!(log?.action_detail || isPgFamily);
  const copyPayload = log ? getCopyPayload(actionDetail) : null;
  const showCopyPayloadSection =
    !!log && (isInAppCopyActionType(log.action_type) || copyPayload != null);

  const normalizedRequestParams = log ? normalizeRequestParamsForDisplay(log.request_params) : null;

  const formatAccessAuditTime = (v) => {
    if (v == null || v === '') return '-';
    try {
      return new Date(v).toLocaleString('ko-KR');
    } catch {
      return String(v);
    }
  };

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

  const renderDiffCell = (val) => {
    if (val === undefined) return '-';
    if (val === null) return plainText(null);
    if (typeof val === 'object') {
      return (
        <pre className="json-content json-pretty activity-log-diff-cell-pre">{formatJSON(val)}</pre>
      );
    }
    return plainText(val);
  };

  /**
   * Unified diff: union of keys from both objects (permission-group metadata and generic UPDATE).
   * @param {unknown} before
   * @param {unknown} after
   * @param {{ excludeKeys?: string[] }} [options]
   */
  const renderObjectDiffTable = (before, after, options = {}) => {
    const exclude = new Set(options.excludeKeys || []);
    const tableAriaLabel = options.tableAriaLabel;
    const emphasizeChangeColumns = options.emphasizeChangeColumns === true;
    const b = asSnapshot(before);
    const a = asSnapshot(after);
    if (!b && !a) return null;
    const keySet = new Set([
      ...Object.keys(b || {}).filter((k) => !exclude.has(k)),
      ...Object.keys(a || {}).filter((k) => !exclude.has(k)),
    ]);
    const fields = Array.from(keySet).sort((x, y) => x.localeCompare(y));
    const beforeThClass = emphasizeChangeColumns ? 'pg-audit-diff-col-before' : undefined;
    const afterThClass = emphasizeChangeColumns ? 'pg-audit-diff-col-after' : undefined;
    return (
      <table
        className="summary-table activity-log-object-diff-table"
        aria-label={tableAriaLabel || undefined}
      >
        <thead>
          <tr>
            <th scope="col">필드</th>
            <th scope="col" className={beforeThClass}>
              변경 전
            </th>
            <th scope="col" className={afterThClass}>
              변경 후
            </th>
          </tr>
        </thead>
        <tbody>
          {fields.map((field) => {
            const beforeVal =
              b && Object.prototype.hasOwnProperty.call(b, field) ? b[field] : undefined;
            const afterVal =
              a && Object.prototype.hasOwnProperty.call(a, field) ? a[field] : undefined;
            const fieldHeader =
              ACTION_DETAIL_FIELD_LABELS[field] != null
                ? ACTION_DETAIL_FIELD_LABELS[field]
                : field;
            return (
              <tr key={field}>
                <th scope="row">{fieldHeader}</th>
                <td>{renderDiffCell(beforeVal)}</td>
                <td>{renderDiffCell(afterVal)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  };

  const renderSnapshotPayloadBlock = (raw, headingText) => {
    const parsed = normalizeSnapshotPayload(raw);
    if (parsed == null) {
      return <p className="pg-audit-empty">없음</p>;
    }
    if (isPlainObject(parsed)) {
      const keys = Object.keys(parsed).sort((x, y) => x.localeCompare(y));
      return (
        <>
          {headingText && <h5 className="pg-audit-subheading">{headingText}</h5>}
          <table className="summary-table activity-log-mutation-kv-table">
            <thead>
              <tr>
                <th scope="col">필드</th>
                <th scope="col">값</th>
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k}>
                  <th scope="row">
                    {ACTION_DETAIL_FIELD_LABELS[k] != null ? ACTION_DETAIL_FIELD_LABELS[k] : k}
                  </th>
                  <td>{renderDiffCell(parsed[k])}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <details className="json-details activity-log-mutation-json-details">
            <summary>원본 JSON 보기</summary>
            <pre className="json-content json-pretty">{formatJSON(parsed)}</pre>
          </details>
        </>
      );
    }
    return (
      <>
        {headingText && <h5 className="pg-audit-subheading">{headingText}</h5>}
        <pre className="json-content json-pretty">{formatJSON(parsed)}</pre>
      </>
    );
  };

  const renderDepartmentAdminAudit = () => {
    const da = actionDetail.department_admin;
    if (!isPlainObject(da)) return null;
    const { before: b, after: a, ...rest } = da;
    const sortedRestKeys = Object.keys(rest).sort((x, y) => x.localeCompare(y));
    const rows = [];
    const remainder = {};
    for (const key of sortedRestKeys) {
      const label = ACTION_DETAIL_FIELD_LABELS[key];
      const val = rest[key];
      if (label) {
        rows.push({ key, label, val });
      } else {
        remainder[key] = val;
      }
    }
    const bObj = isPlainObject(b);
    const aObj = isPlainObject(a);
    const showDiff = bObj && aObj;
    const showPartial =
      !showDiff &&
      ((b != null && b !== '') || (a != null && a !== ''));
    const hasFlat = rows.length > 0 || Object.keys(remainder).length > 0;
    if (!showDiff && !showPartial && !hasFlat) {
      return null;
    }
    return (
      <div className="activity-log-department-admin-audit">
        <h4>부서·조직 감사</h4>
        {showDiff && (
          <>
            <h5 className="pg-audit-subheading">수정 — 필드별 비교</h5>
            {renderObjectDiffTable(b, a, {
              tableAriaLabel: '부서 메타데이터 필드별 변경 전후',
            })}
          </>
        )}
        {showPartial && (
          <>
            {b != null && b !== '' && renderSnapshotPayloadBlock(b, '변경 전')}
            {a != null && a !== '' && renderSnapshotPayloadBlock(a, '변경 후')}
          </>
        )}
        {rows.length > 0 && (
          <table className="summary-table activity-log-labeled-detail-table">
            <tbody>
              {rows.map(({ key, label, val }) => (
                <tr key={key}>
                  <th scope="row">{label}</th>
                  <td>{renderDiffCell(val)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {Object.keys(remainder).length > 0 && (
          <div className="action-detail-unknown-keys">
            <h5 className="pg-audit-subheading">기타 필드 (JSON)</h5>
            <pre className="json-content json-pretty">{formatJSON(remainder)}</pre>
          </div>
        )}
      </div>
    );
  };

  const renderGenericMutationSummary = () => {
    const summary = getGenericMutationSummary(actionDetail);
    if (!summary) return null;
    return (
      <div className="activity-log-mutation-summary">
        <h4>변경·삭제·추가 요약</h4>
        {summary.kind === 'UPDATE' && (
          <>
            <h5 className="pg-audit-subheading">수정 — 필드별 비교</h5>
            {renderObjectDiffTable(summary.before, summary.after)}
          </>
        )}
        {summary.kind === 'DELETE' && renderSnapshotPayloadBlock(summary.snapshot, summary.title)}
        {summary.kind === 'INSERT' && renderSnapshotPayloadBlock(summary.payload, summary.title)}
      </div>
    );
  };

  const renderUserLifecycleAudit = () => {
    if (!log || !isUserLifecycleActionType(log.action_type)) return null;
    if (actionDetail.permissionGroupAuditV1) return null;
    const changeReason = actionDetail.changeReason ?? actionDetail.change_reason;
    const targetUserId = actionDetail.targetUserId ?? actionDetail.target_user_id;
    const employeeNumber = actionDetail.employeeNumber ?? actionDetail.employee_number;
    const username = actionDetail.username;
    const departmentCode = actionDetail.departmentCode ?? actionDetail.department_code;
    const displayName = actionDetail.name;
    const source = actionDetail.source;
    const registrationSource =
      actionDetail.registrationSource ?? actionDetail.registration_source;
    const hasAny =
      (changeReason != null && String(changeReason).trim() !== '') ||
      targetUserId != null ||
      (employeeNumber != null && String(employeeNumber).trim() !== '') ||
      (username != null && String(username).trim() !== '') ||
      (departmentCode != null && String(departmentCode).trim() !== '') ||
      (displayName != null && String(displayName).trim() !== '') ||
      (source != null && String(source).trim() !== '') ||
      (registrationSource != null && String(registrationSource).trim() !== '');
    if (!hasAny) return null;
    return (
      <div className="activity-log-user-lifecycle-audit">
        <h4>사용자 등록·삭제 감사</h4>
        <table className="summary-table">
          <tbody>
            <tr>
              <th scope="row">변경 사유</th>
              <td>
                {changeReason != null && String(changeReason).trim() !== ''
                  ? plainText(changeReason)
                  : '-'}
              </td>
            </tr>
            <tr>
              <th scope="row">대상 사용자 ID (앱)</th>
              <td>{targetUserId != null ? plainText(targetUserId) : '-'}</td>
            </tr>
            <tr>
              <th scope="row">부서 코드</th>
              <td>
                {departmentCode != null && String(departmentCode).trim() !== ''
                  ? plainText(departmentCode)
                  : '-'}
              </td>
            </tr>
            <tr>
              <th scope="row">이름</th>
              <td>
                {displayName != null && String(displayName).trim() !== ''
                  ? plainText(displayName)
                  : '-'}
              </td>
            </tr>
            <tr>
              <th scope="row">사용자 ID (인사)</th>
              <td>
                {employeeNumber != null && String(employeeNumber).trim() !== ''
                  ? plainText(employeeNumber)
                  : '-'}
              </td>
            </tr>
            <tr>
              <th scope="row">로그인 사용자명</th>
              <td>
                {username != null && String(username).trim() !== '' ? plainText(username) : '-'}
              </td>
            </tr>
            <tr>
              <th scope="row">출처</th>
              <td>
                {source != null && String(source).trim() !== '' ? plainText(source) : '-'}
              </td>
            </tr>
            <tr>
              <th scope="row">등록 출처</th>
              <td>
                {registrationSource != null && String(registrationSource).trim() !== ''
                  ? plainText(registrationSource)
                  : '-'}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  };

  const renderLabeledFlatActionDetail = () => {
    if (isPgFamily) return null;
    const excludeLifecycle = getUserLifecycleDetailExcludedKeys(log?.action_type);
    const rows = [];
    const remainder = {};
    const keys = Object.keys(actionDetail).sort((x, y) => x.localeCompare(y));
    for (const key of keys) {
      if (ACTION_DETAIL_STRUCTURAL_KEYS.has(key)) continue;
      if (excludeLifecycle.has(key)) continue;
      if (LEGACY_PG_ENRICHER_KEYS.has(key)) continue;
      const val = actionDetail[key];
      const label = ACTION_DETAIL_FIELD_LABELS[key];
      if (label) {
        rows.push({ key, label, val });
      } else {
        remainder[key] = val;
      }
    }
    if (rows.length === 0 && Object.keys(remainder).length === 0) return null;
    return (
      <>
        {rows.length > 0 && (
          <div className="activity-log-labeled-action-detail">
            <h4>상세 필드</h4>
            <table className="summary-table activity-log-labeled-detail-table">
              <tbody>
                {rows.map(({ key, label, val }) => (
                  <tr key={key}>
                    <th scope="row">{label}</th>
                    <td>{renderDiffCell(val)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {Object.keys(remainder).length > 0 && (
          <div className="action-detail-unknown-keys">
            <h4>기타 키 (JSON)</h4>
            <pre className="json-content json-pretty">{formatJSON(remainder)}</pre>
          </div>
        )}
      </>
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
            {op === 'ASSIGN_USER' && before == null && after != null && (
              <p className="pg-audit-snapshot-note" role="status">
                이전 권한 그룹 없음 — 첫 배정이거나 변경 전 소속 그룹이 없었습니다. 아래 &quot;변경 전&quot; 열의
                &quot;-&quot;는 누락이 아니라 이전 스냅샷이 없음을 뜻합니다.
              </p>
            )}
            {op === 'UNASSIGN_USER' && before != null && after == null && (
              <p className="pg-audit-snapshot-note" role="status">
                변경 후에는 이 권한 그룹에 속하지 않습니다. 아래 &quot;변경 후&quot; 열의 &quot;-&quot;는 해제
                이후 스냅샷이 없음을 뜻합니다.
              </p>
            )}
            {renderObjectDiffTable(before, after, {
              excludeKeys: ['allowedScreens'],
              tableAriaLabel: '권한 그룹 메타데이터 필드별 변경 전후',
              emphasizeChangeColumns: true,
            })}
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
      <div
        className="activity-log-detail-modal"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="activity-log-detail-title"
      >
        <div className="activity-log-detail-header">
          <h2 id="activity-log-detail-title">활동 이력 상세</h2>
          <button type="button" className="close-button" onClick={onClose} aria-label="상세 닫기">
            ×
          </button>
        </div>
        <div className="activity-log-detail-body">
          <div className="activity-log-detail-content">
          {loading && (
            <div className="activity-log-detail-loading" role="status" aria-live="polite">
              상세를 불러오는 중…
            </div>
          )}
          {error && !loading && (
            <div className="activity-log-detail-fetch-error" role="alert">
              {error}
            </div>
          )}
          {log && !loading && !error && (
            <>
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

          {normalizedRequestParams != null && (
            <div className="detail-section">
              <h3>요청 파라미터 (request_params)</h3>
              <pre className="json-content json-pretty">{formatJSON(normalizedRequestParams)}</pre>
            </div>
          )}

          {showActionDetailSection && (
            <div className="detail-section">
              <h3>액션 상세</h3>
              {renderGenericMutationSummary()}
              {renderDepartmentAdminAudit()}
              {renderUserLifecycleAudit()}
              {renderLabeledFlatActionDetail()}
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

              {showCopyPayloadSection && (
                <div className="activity-log-copy-subsection">
                  <h4>In-app copy</h4>
                  {copyPayload ? (
                    <>
                      {(copyPayload.was_truncated === true || copyPayload.wasTruncated === true) && (
                        <span className="badge badge-warning activity-log-truncated-badge">Truncated</span>
                      )}
                      <p className="activity-log-copy-meta">
                        Character count (reported):{' '}
                        {copyPayload.length != null ? String(copyPayload.length) : '—'}
                      </p>
                      <pre className="json-content json-pretty activity-log-copy-preview">
                        {revealedCopyBody != null ? revealedCopyBody : plainText(copyPayload.text) || '—'}
                      </pre>
                      {canOfferPrivilegedCopyReveal(log, copyPayload) && revealedCopyBody == null && (
                        <button
                          type="button"
                          className="btn btn-secondary activity-log-reveal-copy-btn"
                          onClick={handleRevealFullCopy}
                          disabled={revealLoading}
                        >
                          {revealLoading ? 'Loading…' : 'View full copy body'}
                        </button>
                      )}
                      {revealError && (
                        <p className="activity-log-reveal-error" role="alert">
                          {revealError}
                        </p>
                      )}
                    </>
                  ) : (
                    <p className="pg-audit-empty">copyPayload가 응답에 없습니다.</p>
                  )}
                </div>
              )}
            </div>
          )}

          {canOpenAccessAudit && accessAuditState?.status === 'success' && (
            <div className="detail-section activity-log-embedded-access-audit-section">
              <details className="activity-log-embedded-access-audit">
                <summary>
                  이 로그에 대한 접근 감사 ({accessAuditState.rows.length}건)
                </summary>
                {accessAuditState.rows.length === 0 ? (
                  <p className="pg-audit-empty">접근 기록이 없습니다.</p>
                ) : (
                  <div className="activity-log-access-audit-embed-wrap">
                    <table className="summary-table activity-log-access-audit-embed-table">
                      <thead>
                        <tr>
                          <th scope="col">접근자 ID</th>
                          <th scope="col">접근자 이름</th>
                          <th scope="col">일시</th>
                          <th scope="col">대상 로그 ID</th>
                          <th scope="col">접근 유형</th>
                        </tr>
                      </thead>
                      <tbody>
                        {accessAuditState.rows.map((row, idx) => (
                          <tr
                            key={`${String(row.accessedAt ?? row.accessed_at ?? idx)}-${String(row.accessorUserId ?? row.accessor_user_id ?? '')}-${idx}`}
                          >
                            <td>{plainText(row.accessorUserId ?? row.accessor_user_id)}</td>
                            <td>{plainText(row.accessorDisplayName ?? row.accessor_display_name)}</td>
                            <td>{formatAccessAuditTime(row.accessedAt ?? row.accessed_at)}</td>
                            <td>{plainText(row.targetActivityLogId ?? row.target_activity_log_id)}</td>
                            <td>{plainText(row.accessType ?? row.access_type)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </details>
              <p className="activity-log-embedded-access-audit-hint">
                필터·검색이 필요하면 하단 링크로 전체 화면을 이용할 수 있습니다.
              </p>
            </div>
          )}
            </>
          )}
          </div>
        </div>
        <div className="activity-log-detail-footer">
          {canOpenAccessAudit && onNavigateToAccessAudit && log?.id != null && (
            <button
              type="button"
              className="btn btn-link activity-log-open-access-audit-link"
              onClick={() => onNavigateToAccessAudit(log.id)}
            >
              접근 감사 전체 화면으로 이동
            </button>
          )}
          <button type="button" className="btn btn-primary" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};

export default UserActivityLogDetail;
