import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  getDepartments,
  getDepartmentMembers,
  getDepartmentApprovers,
  addDepartmentApprover,
  addDefaultApprovers,
  removeDepartmentApprover,
} from '../../services/departmentService';
import DataTable, { EmptyTableBody } from '../DataTable';
import logger from '../../utils/logger';
import '../UserManagement/UserManagement.css';
import './DepartmentApproverManagement.css';

const DEPT_APPROVER_COLUMNS = [
  { key: 'userId', label: '사용자 ID', sortable: true },
  { key: 'role', label: '역할', sortable: true },
  { key: 'position', label: '직책', sortable: true },
  { key: 'departmentCode', label: '부서코드', sortable: true },
  { key: 'actions', label: '동작', sortable: false },
];

/** API error code → 사용자 메시지 (docs/api-definition.md §12) */
const getErrorMessage = (e, fallback) => {
  const code = e?.code;
  const status = e?.status;
  if (code === 'DEPARTMENT_NOT_FOUND') return '부서를 찾을 수 없습니다.';
  if (code === 'USER_NOT_FOUND') return '해당 사용자를 찾을 수 없습니다.';
  if (code === 'ALREADY_APPROVER') return '이미 해당 부서 결재자로 등록되어 있습니다.';
  if (code === 'USER_NOT_IN_DEPARTMENT') return '지정한 사용자가 해당 부서 소속이 아님. 부서별 결재자로 추가 불가.';
  if (status === 403) return '권한이 없습니다.';
  if (status === 404) return '부서를 찾을 수 없습니다.';
  if (status === 400) return code ? (e?.message || fallback) : (e?.message || '잘못된 요청입니다.');
  return e?.message || fallback;
};

const DepartmentTree = ({ nodes, selectedCode, onSelect, level = 0 }) => {
  if (!nodes || nodes.length === 0) return null;
  return (
    <ul className="dept-tree-list" role="tree">
      {nodes.map((node) => {
        const code = node.code;
        const hasChildren = node.children && node.children.length > 0;
        const isSelected = selectedCode === code;
        const isExpanded = true;
        return (
          <li
            key={code}
            className="dept-tree-item"
            role="treeitem"
            aria-expanded={hasChildren ? isExpanded : undefined}
            aria-selected={isSelected}
            style={{ paddingLeft: `${level * 1.25}rem` }}
          >
            <button
              type="button"
              className={`dept-tree-node ${isSelected ? 'selected' : ''}`}
              onClick={() => onSelect(code, node)}
              title={`${code} ${node.name || ''}`}
            >
              <span className="dept-tree-label">
                [{code}] {node.name || code}
              </span>
            </button>
            {hasChildren && (
              <DepartmentTree
                nodes={node.children}
                selectedCode={selectedCode}
                onSelect={onSelect}
                level={level + 1}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
};

const DepartmentApproverManagement = ({ user }) => {
  const [tree, setTree] = useState([]);
  const [selectedDept, setSelectedDept] = useState(null);
  const [members, setMembers] = useState([]);
  const [approvers, setApprovers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [membersLoading, setMembersLoading] = useState(false);
  const [approversLoading, setApproversLoading] = useState(false);
  const [error, setError] = useState(null);
  const [actionId, setActionId] = useState(null);
  const [addUserId, setAddUserId] = useState('');
  const selectedCodeRef = useRef(null);

  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    selectedCodeRef.current = selectedDept?.code ?? null;
  }, [selectedDept?.code]);

  const loadTree = async () => {
    if (!isAdmin) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getDepartments('tree');
      const data = result.data;
      setTree(Array.isArray(data) ? data : (data?.data || []));
    } catch (e) {
      logger.error('부서 트리 조회 실패:', e);
      setError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '부서 목록을 불러오지 못했습니다.'));
      setTree([]);
    } finally {
      setLoading(false);
    }
  };

  const loadMembers = async (code) => {
    if (!code) {
      setMembers([]);
      return;
    }
    setMembersLoading(true);
    try {
      const list = await getDepartmentMembers(code);
      if (selectedCodeRef.current === code) setMembers(list);
    } catch (e) {
      logger.error('부서 멤버 조회 실패:', e);
      if (selectedCodeRef.current === code) setMembers([]);
    } finally {
      setMembersLoading(false);
    }
  };

  const loadApprovers = async (code) => {
    if (!code) {
      setApprovers([]);
      return;
    }
    setApproversLoading(true);
    setError(null);
    try {
      const list = await getDepartmentApprovers(code);
      if (selectedCodeRef.current === code) setApprovers(list);
    } catch (e) {
      logger.error('부서 결재자 목록 조회 실패:', e);
      setError(getErrorMessage(e, '결재자 목록을 불러오지 못했습니다.'));
      if (selectedCodeRef.current === code) setApprovers([]);
    } finally {
      setApproversLoading(false);
    }
  };

  useEffect(() => {
    loadTree();
  }, [isAdmin]);

  useEffect(() => {
    if (selectedDept?.code) {
      loadMembers(selectedDept.code);
      loadApprovers(selectedDept.code);
    } else {
      setMembers([]);
      setApprovers([]);
    }
  }, [selectedDept?.code]);

  const handleSelectDept = (code, node) => {
    setSelectedDept({ code, name: node.name });
    setAddUserId('');
  };

  const handleAddDefaultApprovers = async () => {
    const code = selectedDept?.code;
    if (!code) return;
    setActionId('default');
    setError(null);
    try {
      await addDefaultApprovers(code);
      await loadApprovers(code);
      await loadMembers(code);
    } catch (e) {
      logger.error('팀장 지정 실패:', e);
      setError(getErrorMessage(e, '팀장 지정에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleAddApprover = async () => {
    const code = selectedDept?.code;
    const userId = addUserId?.trim();
    if (!code || !userId) return;
    setActionId(`add-${userId}`);
    setError(null);
    try {
      await addDepartmentApprover(code, userId);
      await loadApprovers(code);
      setAddUserId('');
    } catch (e) {
      logger.error('부서 결재자 추가 실패:', e);
      setError(getErrorMessage(e, '결재자 지정에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const handleRemoveApprover = async (userId) => {
    const code = selectedDept?.code;
    if (!code) return;
    setActionId(userId);
    setError(null);
    try {
      await removeDepartmentApprover(code, userId);
      await loadApprovers(code);
    } catch (e) {
      logger.error('부서 결재자 해제 실패:', e);
      setError(getErrorMessage(e, '결재자 해제에 실패했습니다.'));
    } finally {
      setActionId(null);
    }
  };

  const alreadyApprover = (userId) => approvers.some((a) => (a.userId || a.username) === userId);
  const addableUsers = members.filter((u) => {
    const id = u.userId ?? u.username;
    return id && !(u.isApprover || alreadyApprover(id));
  });
  const teamLeadersNotYetApprovers = members.filter((u) => {
    const pos = u.position;
    const isTeamLeader = pos && String(pos).includes('팀장');
    const id = u.userId ?? u.username;
    return isTeamLeader && !(u.isApprover || alreadyApprover(id));
  });
  const canAddDefaultApprovers = teamLeadersNotYetApprovers.length > 0;

  const [sortConfig, setSortConfig] = useState({ key: 'userId', direction: 'asc' });
  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };
  const sortedApprovers = useMemo(() => {
    if (!approvers.length || !sortConfig.key) return approvers;
    const key = sortConfig.key;
    const dir = sortConfig.direction === 'asc' ? 1 : -1;
    const getVal = (row, k) => {
      if (k === 'departmentCode') return row.departmentCode ?? row.department_code;
      if (k === 'position') return row.position;
      return row[k];
    };
    return [...approvers].sort((a, b) => {
      const va = getVal(a, key);
      const vb = getVal(b, key);
      if (va == null && vb == null) return 0;
      if (va == null) return dir;
      if (vb == null) return -dir;
      return dir * String(va).localeCompare(String(vb));
    });
  }, [approvers, sortConfig.key, sortConfig.direction]);

  if (!isAdmin) {
    return (
      <div className="department-approver-management">
        <h2>부서별 결재자 지정</h2>
        <p className="department-approver-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="department-approver-management">
      <h2>부서별 결재자 지정</h2>
      {error && (
        <div className="user-management-error" role="alert">
          {error}
        </div>
      )}
      <p className="department-approver-hint">부서를 선택하면 해당 부서의 결재자를 관리할 수 있습니다.</p>
      {loading ? (
        <p>목록을 불러오는 중...</p>
      ) : tree.length === 0 ? (
        <p>등록된 부서가 없습니다.</p>
      ) : (
        <div className="department-approver-layout">
          <section className="department-tree-section" aria-label="부서 목록">
            <DepartmentTree nodes={tree} selectedCode={selectedDept?.code} onSelect={handleSelectDept} />
          </section>
          <section className="department-approvers-section" aria-label="선택한 부서 결재자">
            {selectedDept ? (
              <>
                <h3>결재자: [{selectedDept.code}] {selectedDept.name || selectedDept.code}</h3>
                {approversLoading || membersLoading ? (
                  <p>목록을 불러오는 중…</p>
                ) : (
                  <>
                    <div className="department-approver-add">
                      <select
                        value={addUserId}
                        onChange={(e) => setAddUserId(e.target.value)}
                        aria-label="추가할 사용자 선택"
                        disabled={members.length === 0}
                      >
                        <option value="">
                          {members.length === 0 ? '해당 부서 사용자 없음' : '— 사용자 선택 —'}
                        </option>
                        {addableUsers.map((u) => {
                          const id = u.userId ?? u.username;
                          const pos = u.position ? ` (${u.position})` : '';
                          return (
                            <option key={id} value={id}>
                              {id}{pos} {u.departmentCode ? `(${u.departmentCode})` : ''}
                            </option>
                          );
                        })}
                      </select>
                      <button
                        type="button"
                        className="user-management-btn add"
                        onClick={handleAddApprover}
                        disabled={!addUserId || actionId != null || members.length === 0}
                        aria-label="결재자 추가"
                      >
                        {actionId && actionId.startsWith('add-') ? '처리 중...' : '결재자 추가'}
                      </button>
                      <button
                        type="button"
                        className="user-management-btn add department-approver-default-btn"
                        onClick={handleAddDefaultApprovers}
                        disabled={!canAddDefaultApprovers || actionId != null}
                        aria-label="팀장 지정"
                        title={!canAddDefaultApprovers ? '해당 부서에 팀장이 없거나 이미 모두 결재자로 지정되어 있습니다.' : undefined}
                      >
                        {actionId === 'default' ? '처리 중...' : '팀장 지정'}
                      </button>
                    </div>
                    <DataTable
                      columns={DEPT_APPROVER_COLUMNS}
                      sortConfig={sortConfig}
                      onSort={handleSort}
                      loading={approversLoading}
                      emptyMessage="해당 부서에 지정된 결재자가 없습니다."
                      emptyColSpan={5}
                      ariaLabel="부서 결재자 목록"
                    >
                      {sortedApprovers.length === 0 ? (
                        <EmptyTableBody colSpan={5} message="해당 부서에 지정된 결재자가 없습니다." />
                      ) : (
                        sortedApprovers.map((row) => {
                          const userId = row.userId ?? row.username;
                          const position = row.position;
                          return (
                            <tr key={userId}>
                              <td>{userId}</td>
                              <td>{row.role || '-'}</td>
                              <td>{position && String(position).trim() ? position : '-'}</td>
                              <td>{row.departmentCode ?? row.department_code ?? '-'}</td>
                              <td>
                                <button type="button" className="user-management-btn remove" onClick={() => handleRemoveApprover(userId)} disabled={actionId === userId} aria-label={`결재자 해제, ${userId}`}>
                                  {actionId === userId ? '처리 중...' : '결재자 해제'}
                                </button>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </DataTable>
                  </>
                )}
              </>
            ) : (
              <p>부서를 선택하세요.</p>
            )}
          </section>
        </div>
      )}
    </div>
  );
};

export default DepartmentApproverManagement;
