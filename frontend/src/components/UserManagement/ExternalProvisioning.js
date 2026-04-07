import React, { useState, useCallback } from 'react';
import {
  searchExternalEmployees,
  provisionUserFromExternalEmployee,
} from '../../services/provisioningService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import '../DataTable.css';
import '../UserPermissionHierarchy/UserPermissionHierarchy.css';
import './UserManagement.css';
import './ExternalProvisioning.css';

/** docs/api-definition.md §2a provisioning — changeReason max length */
const PROVISION_CHANGE_REASON_MAX = 500;

const defaultPagination = { currentPage: 1, totalPages: 1, totalCount: 0 };

/** 8-digit 예시 — DB 시드의 사용자 ID 형식과 맞춤 (예: init-data). */
const USER_ID_SEARCH_PLACEHOLDER = '20261001';

function rowProvisioned(row) {
  return row?.provisioned === true;
}

function provisionedDisplayLabel(row) {
  const username = row?.provisionedUsername ?? row?.provisioned_username;
  const appUserId = row?.provisionedAppUserId ?? row?.provisioned_app_user_id;
  const parts = [];
  if (username != null && String(username).trim() !== '') parts.push(String(username));
  if (appUserId != null && String(appUserId).trim() !== '') parts.push(`ID ${appUserId}`);
  return parts.length ? parts.join(' · ') : '—';
}

function formatProvisionConflictMessage(error) {
  const base = '이미 인사정보로 등록된 직원입니다.';
  const p = error?.payload;
  const existingUsername = p?.existingUsername ?? p?.existing_username;
  const existingAppUserId = p?.existingAppUserId ?? p?.existing_app_user_id;
  const extra = [];
  if (existingUsername != null && String(existingUsername).trim() !== '') {
    extra.push(`기존 사용자명: ${existingUsername}`);
  }
  if (existingAppUserId != null && String(existingAppUserId).trim() !== '') {
    extra.push(`기존 앱 사용자 ID: ${existingAppUserId}`);
  }
  return extra.length ? `${base} ${extra.join(' ')}` : base;
}

/**
 * Admin-only: search replicated HR employee data and register app_user (provisioning APIs).
 * @param {object} props
 * @param {() => void} [props.onProvisioned]
 * @param {boolean} [props.embeddedInModal] — hide page-level chrome when shown inside a dialog
 */
const ExternalProvisioning = ({ onProvisioned, embeddedInModal = false }) => {
  const [departmentName, setDepartmentName] = useState('');
  const [employeeNumber, setEmployeeNumber] = useState('');
  const [keyword, setKeyword] = useState('');
  const [sourceSystem, setSourceSystem] = useState('');
  const [empPage, setEmpPage] = useState(1);
  const [empLoading, setEmpLoading] = useState(false);
  const [empError, setEmpError] = useState(null);
  const [empItems, setEmpItems] = useState([]);
  const [empPagination, setEmpPagination] = useState(defaultPagination);
  const [selectedEmp, setSelectedEmp] = useState(null);
  const [deptCode, setDeptCode] = useState('');
  const [provisionLoading, setProvisionLoading] = useState(false);
  const [provisionMessage, setProvisionMessage] = useState(null);
  const [provisionChangeReason, setProvisionChangeReason] = useState('');

  const searchEmployees = useCallback(
    async (pageOverride) => {
      const page = pageOverride ?? empPage;
      setEmpLoading(true);
      setEmpError(null);
      try {
        const res = await searchExternalEmployees({
          departmentName: departmentName.trim() || undefined,
          keyword: keyword.trim() || undefined,
          employeeNumber: employeeNumber.trim() || undefined,
          sourceSystem: sourceSystem.trim() || undefined,
          page,
          pageSize: 20,
        });
        const data = res.data || res;
        const items = data.items || [];
        setEmpItems(Array.isArray(items) ? items : []);
        setEmpPage(page);
        const p = data.pagination || data.paginationResponse;
        if (p && typeof p === 'object') {
          setEmpPagination({
            currentPage: p.currentPage ?? page,
            totalPages: p.totalPages ?? 1,
            totalCount: p.totalCount ?? items.length,
          });
        } else {
          setEmpPagination({ ...defaultPagination, currentPage: page, totalCount: items.length });
        }
        setSelectedEmp((prev) => {
          if (!prev) return null;
          const prevId = prev.externalEmployeeId ?? prev.external_employee_id;
          const prevSrc = prev.sourceSystem || '';
          const match = items.find((row) => {
            const id = row.externalEmployeeId ?? row.external_employee_id;
            return String(id) === String(prevId) && (row.sourceSystem || '') === prevSrc;
          });
          if (!match) return null;
          if (rowProvisioned(match)) return null;
          return match;
        });
      } catch (e) {
        logger.error('인사정보 직원 검색 실패:', e);
        setEmpError(getErrorMessage(e, '검색에 실패했습니다.'));
        setEmpItems([]);
      } finally {
        setEmpLoading(false);
      }
    },
    [departmentName, keyword, employeeNumber, sourceSystem, empPage]
  );

  const handleProvision = async () => {
    const selId = selectedEmp?.externalEmployeeId ?? selectedEmp?.external_employee_id;
    if (selId == null || String(selId).trim() === '') {
      setProvisionMessage({ type: 'error', text: '등록할 직원 행을 선택하세요.' });
      return;
    }
    if (rowProvisioned(selectedEmp)) {
      setProvisionMessage({ type: 'error', text: '이미 등록된 직원입니다.' });
      return;
    }
    const reasonTrimmed = provisionChangeReason.trim();
    if (!reasonTrimmed) {
      setProvisionMessage({ type: 'error', text: '등록 사유를 입력하세요.' });
      return;
    }
    if (reasonTrimmed.length > PROVISION_CHANGE_REASON_MAX) {
      setProvisionMessage({
        type: 'error',
        text: `등록 사유는 ${PROVISION_CHANGE_REASON_MAX}자 이하여야 합니다.`,
      });
      return;
    }
    setProvisionLoading(true);
    setProvisionMessage(null);
    try {
      const extId = selectedEmp.externalEmployeeId ?? selectedEmp.external_employee_id;
      const body = {
        externalEmployeeId: String(extId),
        sourceSystem: selectedEmp.sourceSystem || sourceSystem.trim() || undefined,
        departmentCode: deptCode.trim() || undefined,
        changeReason: reasonTrimmed,
      };
      const res = await provisionUserFromExternalEmployee(body);
      const data = res.data || res;
      const uid = data?.userId ?? data?.user_id;
      const hrEmpNumber = data?.employeeNumber ?? data?.employee_number;
      const okText =
        hrEmpNumber != null && String(hrEmpNumber).trim() !== ''
          ? `등록되었습니다. 사용자 ID(인사): ${hrEmpNumber} · 앱 내부 ID: ${uid != null ? uid : '—'}`
          : `등록되었습니다. 사용자 ID: ${uid != null ? uid : '—'}`;
      setProvisionMessage({
        type: 'ok',
        text: okText,
      });
      setSelectedEmp(null);
      setProvisionChangeReason('');
      if (onProvisioned) onProvisioned();
    } catch (e) {
      const msg =
        e?.status === 409
          ? formatProvisionConflictMessage(e)
          : getErrorMessage(e, '등록에 실패했습니다.');
      setProvisionMessage({ type: 'error', text: msg });
    } finally {
      setProvisionLoading(false);
    }
  };

  const rootClass = embeddedInModal
    ? 'external-provisioning external-provisioning--embedded'
    : 'external-provisioning';

  const headingId = 'hr-provisioning-search-heading';

  const selectedProvisioned =
    selectedEmp && rowProvisioned(selectedEmp);

  return (
    <section className={rootClass} aria-labelledby={headingId}>
      {!embeddedInModal && (
        <>
          <h3 id={headingId}>인사정보 검색 · 사용자 등록</h3>
          <p className="external-provisioning-intro user-permission-hierarchy-hint">
            인사정보에서 직원·부서를 검색한 뒤 선택한 직원을 앱 사용자로 등록합니다.
          </p>
        </>
      )}
      {embeddedInModal && (
        <h4 id={headingId} className="external-provisioning-subtitle">
          직원 검색
        </h4>
      )}

      <div className="sf-compact-panel external-provisioning-panel">
        {!embeddedInModal && (
          <h4 className="external-provisioning-subtitle">직원 검색</h4>
        )}
        <div className="sf-row sf-block external-provisioning-search-row">
          <div>
            <label htmlFor="prov-dept-name">부서</label>
            <input
              id="prov-dept-name"
              className="sf-control"
              type="text"
              value={departmentName}
              onChange={(e) => setDepartmentName(e.target.value)}
              placeholder="부서명"
            />
          </div>
          <div>
            <label htmlFor="prov-emp-number">사용자 ID</label>
            <input
              id="prov-emp-number"
              className="sf-control"
              type="text"
              value={employeeNumber}
              onChange={(e) => setEmployeeNumber(e.target.value)}
              placeholder={USER_ID_SEARCH_PLACEHOLDER}
            />
          </div>
          <div>
            <label htmlFor="prov-emp-keyword">직원명</label>
            <input
              id="prov-emp-keyword"
              className="sf-control"
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="표시명 일부"
            />
          </div>
          <div>
            <label htmlFor="prov-source">소스 시스템 (선택)</label>
            <input
              id="prov-source"
              className="sf-control"
              type="text"
              value={sourceSystem}
              onChange={(e) => setSourceSystem(e.target.value)}
              placeholder="sourceSystem"
            />
          </div>
          <button
            type="button"
            className="sf-btn"
            aria-label="직원 검색 실행"
            onClick={() => searchEmployees(1)}
          >
            검색
          </button>
        </div>
        {empError && (
          <div className="external-provisioning-alert" role="alert">
            {empError}
          </div>
        )}
        {empLoading ? (
          <p aria-live="polite">검색 중…</p>
        ) : (
          <div className="log-table-container external-provisioning-table-container">
            <div className="table-wrapper">
              <table
                className="log-table hierarchy-users-table user-management-table external-provisioning-emp-table"
                aria-label="인사정보 직원 검색 결과"
              >
                <thead>
                  <tr>
                    <th scope="col">선택</th>
                    <th scope="col">표시명</th>
                    <th scope="col">부서</th>
                    <th scope="col">사용자 ID</th>
                    <th scope="col">인사정보 직원 ID</th>
                    <th scope="col">소스</th>
                    <th scope="col">인사정보 부서 ID</th>
                    <th scope="col">직책</th>
                    <th scope="col">등록</th>
                  </tr>
                </thead>
                <tbody>
                  {empItems.length === 0 ? (
                    <tr>
                      <td colSpan={9}>검색 결과가 없습니다.</td>
                    </tr>
                  ) : (
                    empItems.map((row) => {
                      const id = row.externalEmployeeId ?? row.external_employee_id;
                      const key = `${id}-${row.sourceSystem || ''}`;
                      const prov = rowProvisioned(row);
                      const checked =
                        selectedEmp &&
                        String(selectedEmp.externalEmployeeId ?? selectedEmp.external_employee_id) ===
                          String(id) &&
                        (selectedEmp.sourceSystem || '') === (row.sourceSystem || '');
                      const rowTitle = prov
                        ? '이 직원은 이미 앱 사용자와 연결되어 있습니다.'
                        : undefined;
                      return (
                        <tr key={key} title={rowTitle}>
                          <td>
                            <input
                              type="radio"
                              name="pick-emp"
                              checked={!!checked}
                              disabled={prov}
                              onChange={() => {
                                if (!prov) setSelectedEmp(row);
                              }}
                              aria-label={
                                prov
                                  ? `이미 등록됨 ${row.displayName || id}`
                                  : `선택 ${row.displayName || id}`
                              }
                              title={prov ? rowTitle : undefined}
                            />
                          </td>
                          <td>{row.displayName ?? '—'}</td>
                          <td>{row.departmentName ?? '—'}</td>
                          <td>{row.employeeNumber ?? '—'}</td>
                          <td>{id ?? '—'}</td>
                          <td>{row.sourceSystem ?? '—'}</td>
                          <td>{row.externalDepartmentId ?? '—'}</td>
                          <td>{row.jobTitle ?? '—'}</td>
                          <td>
                            {prov ? (
                              <span className="external-provisioning-provisioned-cell">
                                <span className="external-provisioning-provisioned-badge">등록됨</span>
                                <span className="external-provisioning-provisioned-detail">
                                  {provisionedDisplayLabel(row)}
                                </span>
                              </span>
                            ) : (
                              '—'
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
        <div className="external-provisioning-pagination">
          <button
            type="button"
            className="sf-btn"
            disabled={empPage <= 1 || empLoading}
            onClick={() => searchEmployees(Math.max(1, empPage - 1))}
          >
            이전
          </button>
          <span>
            {empPagination.currentPage} / {empPagination.totalPages} (총 {empPagination.totalCount}건)
          </span>
          <button
            type="button"
            className="sf-btn"
            disabled={empPage >= empPagination.totalPages || empLoading}
            onClick={() => searchEmployees(empPage + 1)}
          >
            다음
          </button>
        </div>

        <div className="sf-row sf-block external-provisioning-register">
          <div>
            <label htmlFor="prov-dept-code">앱 부서 코드 (선택, FK)</label>
            <input
              id="prov-dept-code"
              className="sf-control"
              type="text"
              value={deptCode}
              onChange={(e) => setDeptCode(e.target.value)}
              placeholder="department.code"
            />
          </div>
          <div className="external-provisioning-reason-field">
            <label htmlFor="prov-change-reason">
              등록 사유 <span aria-hidden="true">*</span>
            </label>
            <textarea
              id="prov-change-reason"
              className="sf-control external-provisioning-reason-textarea"
              rows={4}
              maxLength={PROVISION_CHANGE_REASON_MAX}
              value={provisionChangeReason}
              onChange={(e) => setProvisionChangeReason(e.target.value)}
              placeholder="필수 입력 (전송 시 앞뒤 공백 제거)"
              aria-required="true"
              autoComplete="off"
            />
            <span className="external-provisioning-reason-hint" aria-live="polite">
              {provisionChangeReason.trim().length}/{PROVISION_CHANGE_REASON_MAX}
            </span>
          </div>
          <button
            type="button"
            className="sf-btn sf-btn-primary user-management-btn-primary"
            disabled={provisionLoading || !selectedEmp || selectedProvisioned}
            title={selectedProvisioned ? '이 직원은 이미 앱 사용자와 연결되어 있습니다.' : undefined}
            onClick={handleProvision}
          >
            선택 직원 등록
          </button>
        </div>
        {provisionMessage && (
          <div
            className={
              provisionMessage.type === 'ok' ? 'external-provisioning-success' : 'external-provisioning-alert'
            }
            role="status"
          >
            {provisionMessage.text}
          </div>
        )}
      </div>
    </section>
  );
};

export default ExternalProvisioning;
