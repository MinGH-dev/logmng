import React, { useState, useCallback } from 'react';
import {
  searchExternalEmployees,
  searchExternalDepartments,
  provisionUserFromExternalEmployee,
} from '../../services/provisioningService';
import { getErrorMessage } from '../../utils/errorMessage';
import logger from '../../utils/logger';
import './ExternalProvisioning.css';

const defaultPagination = { currentPage: 1, totalPages: 1, totalCount: 0 };

/**
 * Admin-only: search ext_employee / ext_department and register app_user (provisioning APIs).
 */
const ExternalProvisioning = ({ onProvisioned }) => {
  const [empKeyword, setEmpKeyword] = useState('');
  const [empNumber, setEmpNumber] = useState('');
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

  const [deptKeyword, setDeptKeyword] = useState('');
  const [deptPage, setDeptPage] = useState(1);
  const [deptLoading, setDeptLoading] = useState(false);
  const [deptError, setDeptError] = useState(null);
  const [deptItems, setDeptItems] = useState([]);
  const [deptPagination, setDeptPagination] = useState(defaultPagination);

  const searchEmployees = useCallback(
    async (pageOverride) => {
      const page = pageOverride ?? empPage;
      setEmpLoading(true);
      setEmpError(null);
      try {
        const res = await searchExternalEmployees({
          keyword: empKeyword.trim() || undefined,
          employeeNumber: empNumber.trim() || undefined,
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
      } catch (e) {
        logger.error('외부 직원 검색 실패:', e);
        setEmpError(getErrorMessage(e, '검색에 실패했습니다.'));
        setEmpItems([]);
      } finally {
        setEmpLoading(false);
      }
    },
    [empKeyword, empNumber, sourceSystem, empPage]
  );

  const searchDepartments = useCallback(
    async (pageOverride) => {
      const page = pageOverride ?? deptPage;
      setDeptLoading(true);
      setDeptError(null);
      try {
        const res = await searchExternalDepartments({
          keyword: deptKeyword.trim() || undefined,
          sourceSystem: sourceSystem.trim() || undefined,
          page,
          pageSize: 20,
        });
        const data = res.data || res;
        const items = data.items || [];
        setDeptItems(Array.isArray(items) ? items : []);
        setDeptPage(page);
        const p = data.pagination;
        if (p && typeof p === 'object') {
          setDeptPagination({
            currentPage: p.currentPage ?? page,
            totalPages: p.totalPages ?? 1,
            totalCount: p.totalCount ?? items.length,
          });
        } else {
          setDeptPagination({ ...defaultPagination, currentPage: page, totalCount: items.length });
        }
      } catch (e) {
        logger.error('외부 부서 검색 실패:', e);
        setDeptError(getErrorMessage(e, '검색에 실패했습니다.'));
        setDeptItems([]);
      } finally {
        setDeptLoading(false);
      }
    },
    [deptKeyword, sourceSystem, deptPage]
  );

  const handleProvision = async () => {
    if (!selectedEmp?.externalEmployeeId) {
      setProvisionMessage({ type: 'error', text: '등록할 직원 행을 선택하세요.' });
      return;
    }
    setProvisionLoading(true);
    setProvisionMessage(null);
    try {
      const body = {
        externalEmployeeId: String(selectedEmp.externalEmployeeId),
        sourceSystem: selectedEmp.sourceSystem || sourceSystem.trim() || undefined,
        departmentCode: deptCode.trim() || undefined,
      };
      const res = await provisionUserFromExternalEmployee(body);
      const data = res.data || res;
      const uid = data?.userId ?? data?.user_id;
      setProvisionMessage({
        type: 'ok',
        text: `등록되었습니다. 사용자 ID: ${uid != null ? uid : '—'}`,
      });
      setSelectedEmp(null);
      if (onProvisioned) onProvisioned();
    } catch (e) {
      const msg =
        e?.status === 409
          ? '이미 동일 외부 키로 등록된 사용자가 있습니다.'
          : getErrorMessage(e, '등록에 실패했습니다.');
      setProvisionMessage({ type: 'error', text: msg });
    } finally {
      setProvisionLoading(false);
    }
  };

  return (
    <section className="external-provisioning" aria-labelledby="external-provisioning-heading">
      <h3 id="external-provisioning-heading">외부 조직 데이터에서 사용자 등록</h3>
      <p className="external-provisioning-intro">
        복제된 외부 직원·부서 테이블을 검색한 뒤, 선택한 직원을 앱 사용자로 등록합니다.
      </p>

      <div className="sf-compact-panel external-provisioning-panel">
        <h4 className="external-provisioning-subtitle">외부 직원 검색</h4>
        <div className="sf-row sf-block">
          <div>
            <label htmlFor="prov-emp-keyword">이름 키워드</label>
            <input
              id="prov-emp-keyword"
              className="sf-control"
              type="text"
              value={empKeyword}
              onChange={(e) => setEmpKeyword(e.target.value)}
              placeholder="이름 일부"
            />
          </div>
          <div>
            <label htmlFor="prov-emp-number">사번</label>
            <input
              id="prov-emp-number"
              className="sf-control"
              type="text"
              value={empNumber}
              onChange={(e) => setEmpNumber(e.target.value)}
              placeholder="사번"
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
            aria-label="외부 직원 검색 실행"
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
          <div className="external-provisioning-table-wrap">
            <table className="log-table external-provisioning-table">
              <thead>
                <tr>
                  <th scope="col">선택</th>
                  <th scope="col">표시명</th>
                  <th scope="col">사번</th>
                  <th scope="col">외부 직원 ID</th>
                  <th scope="col">소스</th>
                  <th scope="col">외부 부서 ID</th>
                  <th scope="col">직책</th>
                </tr>
              </thead>
              <tbody>
                {empItems.length === 0 ? (
                  <tr>
                    <td colSpan={7}>검색 결과가 없습니다.</td>
                  </tr>
                ) : (
                  empItems.map((row) => {
                    const id = row.externalEmployeeId ?? row.external_employee_id;
                    const key = `${id}-${row.sourceSystem || ''}`;
                    const checked =
                      selectedEmp &&
                      String(selectedEmp.externalEmployeeId ?? selectedEmp.external_employee_id) === String(id) &&
                      (selectedEmp.sourceSystem || '') === (row.sourceSystem || '');
                    return (
                      <tr key={key}>
                        <td>
                          <input
                            type="radio"
                            name="pick-emp"
                            checked={!!checked}
                            onChange={() => setSelectedEmp(row)}
                            aria-label={`선택 ${row.displayName || id}`}
                          />
                        </td>
                        <td>{row.displayName ?? '—'}</td>
                        <td>{row.employeeNumber ?? '—'}</td>
                        <td>{id ?? '—'}</td>
                        <td>{row.sourceSystem ?? '—'}</td>
                        <td>{row.externalDepartmentId ?? '—'}</td>
                        <td>{row.jobTitle ?? '—'}</td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
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
          <button
            type="button"
            className="sf-btn sf-btn-primary"
            disabled={provisionLoading || !selectedEmp}
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

      <div className="sf-compact-panel external-provisioning-panel">
        <h4 className="external-provisioning-subtitle">외부 부서 검색</h4>
        <div className="sf-row sf-block">
          <div>
            <label htmlFor="prov-dept-keyword">키워드</label>
            <input
              id="prov-dept-keyword"
              className="sf-control"
              type="text"
              value={deptKeyword}
              onChange={(e) => setDeptKeyword(e.target.value)}
              placeholder="부서명 일부"
            />
          </div>
          <button
            type="button"
            className="sf-btn"
            aria-label="외부 부서 검색 실행"
            onClick={() => searchDepartments(1)}
          >
            검색
          </button>
        </div>
        {deptError && (
          <div className="external-provisioning-alert" role="alert">
            {deptError}
          </div>
        )}
        {deptLoading ? (
          <p aria-live="polite">검색 중…</p>
        ) : (
          <div className="external-provisioning-table-wrap">
            <table className="log-table external-provisioning-table">
              <thead>
                <tr>
                  <th scope="col">외부 부서 ID</th>
                  <th scope="col">이름</th>
                  <th scope="col">소스</th>
                  <th scope="col">상위 외부 부서 ID</th>
                </tr>
              </thead>
              <tbody>
                {deptItems.length === 0 ? (
                  <tr>
                    <td colSpan={4}>검색 결과가 없습니다.</td>
                  </tr>
                ) : (
                  deptItems.map((row) => {
                    const eid = row.externalDepartmentId ?? row.external_department_id;
                    return (
                      <tr key={`${eid}-${row.sourceSystem || ''}`}>
                        <td>{eid ?? '—'}</td>
                        <td>{row.name ?? '—'}</td>
                        <td>{row.sourceSystem ?? '—'}</td>
                        <td>{row.parentExternalDepartmentId ?? '—'}</td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
        <div className="external-provisioning-pagination">
          <button
            type="button"
            className="sf-btn"
            disabled={deptPage <= 1 || deptLoading}
            onClick={() => searchDepartments(Math.max(1, deptPage - 1))}
          >
            이전
          </button>
          <span>
            {deptPagination.currentPage} / {deptPagination.totalPages} (총 {deptPagination.totalCount}건)
          </span>
          <button
            type="button"
            className="sf-btn"
            disabled={deptPage >= deptPagination.totalPages || deptLoading}
            onClick={() => searchDepartments(deptPage + 1)}
          >
            다음
          </button>
        </div>
      </div>
    </section>
  );
};

export default ExternalProvisioning;
