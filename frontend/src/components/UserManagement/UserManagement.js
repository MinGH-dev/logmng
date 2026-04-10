import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  TextField,
} from '@mui/material';
import {
  deleteUser,
  createChildDepartmentV2,
  updateDepartmentV2,
  createDirectUserV2,
  deleteDepartmentV2,
  getQuickEntryOptionsV2,
} from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';
import { getErrorMessage } from '../../utils/errorMessage';
import { getAllowedScreenIds, getEmployeeNumberDisplay, getScreenFunctions, getSelfContextForDisplay } from '../../utils/security';
import logger from '../../utils/logger';
import UserGroupAssignment from '../UserGroupAssignment/UserGroupAssignment';
import '../UserPermissionHierarchy/UserPermissionHierarchy.css';
import '../../styles/search-filter-standard.css';
import './UserManagement.css';

/** docs/api-definition.md §7 DELETE user — changeReason max length */
const USER_DELETE_CHANGE_REASON_MAX = 500;
const V2_CHANGE_REASON_MAX = 500;

const emptyQuickEntryField = { previous: null, recent: [] };

/** Synthetic bucket for users without department_code; not a row in `department` (see hierarchy API). */
const UNASSIGNED_DEPARTMENT_CODE = '__UNASSIGNED__';
const EMPLOYEE_NUMBER_FALLBACK_TEXT = '사번 미등록';

/** User-facing label: department name only; never exposes department code in UI. */
const departmentUiLabel = (name) => {
  const t = name != null ? String(name).trim() : '';
  return t || '부서명 미등록';
};

const normalizeMatch = (s) => {
  if (s == null) return '';
  return String(s).trim().toLowerCase();
};

const userMatchesNameAndEmp = (u, nameQ, empQ) => {
  const uname = normalizeMatch(u.userName ?? u.username ?? '');
  const empRaw = u.employeeNumber ?? u.employee_number ?? '';
  const emp = normalizeMatch(String(empRaw));
  return (!nameQ || uname.includes(nameQ)) && (!empQ || emp.includes(empQ));
};

const deptDisplayMatches = (node, deptQ) => {
  if (!deptQ) return true;
  return normalizeMatch(departmentUiLabel(node.name)).includes(deptQ);
};

/** Collect ancestor + node codes so every match is visible when branches expand. */
const collectExpandCodesForMatches = (nodes, deptQ, nameQ, empQ) => {
  const codes = new Set();
  const hasQuery = !!(deptQ || nameQ || empQ);
  if (!hasQuery) return codes;

  const visit = (node, ancestors) => {
    const path = [...ancestors, node.code];
    const dOk = deptDisplayMatches(node, deptQ);
    let hit = false;
    const deptOnly = !!(deptQ && !nameQ && !empQ);

    if (dOk && deptOnly) {
      hit = normalizeMatch(departmentUiLabel(node.name)).includes(deptQ);
    } else if (dOk) {
      for (const u of node.users || []) {
        if (userMatchesNameAndEmp(u, nameQ, empQ)) {
          hit = true;
          break;
        }
      }
      if (!hit && deptQ) {
        hit = normalizeMatch(departmentUiLabel(node.name)).includes(deptQ);
      }
    }

    let childHit = false;
    for (const ch of node.children || []) {
      if (visit(ch, path)) childHit = true;
    }

    const branchHit = hit || childHit;
    if (branchHit) {
      path.forEach((c) => codes.add(c));
    }
    return branchHit;
  };

  (nodes || []).forEach((n) => visit(n, []));
  return codes;
};

/** Client-side filtered tree; returns new structure (does not mutate). */
const filterHierarchyNodes = (nodes, deptQ, nameQ, empQ) => {
  const hasQuery = !!(deptQ || nameQ || empQ);
  if (!hasQuery) return nodes;

  const mapNode = (node) => {
    const dOk = deptDisplayMatches(node, deptQ);
    const deptOnly = !!(deptQ && !nameQ && !empQ);
    const selfDeptHit = !!(deptQ && normalizeMatch(departmentUiLabel(node.name)).includes(deptQ));

    const mappedChildren = (node.children || []).map(mapNode).filter(Boolean);

    if (deptOnly) {
      if (selfDeptHit) {
        return { ...node, users: [...(node.users || [])], children: mappedChildren };
      }
      if (mappedChildren.length === 0) return null;
      return { ...node, users: [], children: mappedChildren };
    }

    const filteredUsers = (node.users || []).filter(
      (u) => dOk && userMatchesNameAndEmp(u, nameQ, empQ)
    );

    if (!dOk) {
      if (mappedChildren.length === 0) return null;
      return { ...node, users: [], children: mappedChildren };
    }

    if (filteredUsers.length === 0 && mappedChildren.length === 0) return null;

    return { ...node, users: filteredUsers, children: mappedChildren };
  };

  return (nodes || []).map(mapNode).filter(Boolean);
};

const collectAllExpandableCodes = (nodes, into = new Set()) => {
  (nodes || []).forEach((node) => {
    const hasChildren = node.children && node.children.length > 0;
    const hasUsers = node.users && node.users.length > 0;
    if (hasChildren || hasUsers) into.add(node.code);
    collectAllExpandableCodes(node.children, into);
  });
  return into;
};

const toQuickEntryField = (field) => ({
  previous: field?.previous ?? null,
  recent: Array.isArray(field?.recent) ? field.recent : [],
});

const HierarchyTree = ({
  nodes,
  expandedCodes,
  onToggle,
  level = 0,
  isRoot = false,
  renderUserRow,
  allGroups,
  onRefresh,
  onSelectDepartment,
  selectedDepartmentCode,
  canWrite,
  onOpenAddDepartment,
  onOpenEditDepartment,
  onOpenAddUser,
  onOpenDeleteDepartment,
}) => {
  if (!nodes || nodes.length === 0) return null;
  return (
    <ul
      className="dept-tree-list"
      role={isRoot ? 'tree' : 'group'}
      aria-label={isRoot ? '부서별 사용자 계층' : undefined}
    >
      {nodes.map((node) => {
        const code = node.code;
        const hasChildren = node.children && node.children.length > 0;
        const users = node.users || [];
        const hasUsers = users.length > 0;
        const canExpand = hasChildren || hasUsers;
        const isExpanded = expandedCodes.has(code);
        return (
          <li
            key={code}
            className="dept-tree-item"
            role="treeitem"
            aria-expanded={canExpand ? isExpanded : undefined}
            aria-selected={false}
            style={{ paddingLeft: `${level * 1.25}rem` }}
          >
            <div className="dept-tree-node-wrapper">
              {canExpand ? (
                <button
                  type="button"
                  className="dept-tree-toggle"
                  onClick={() => onToggle(code)}
                  aria-expanded={isExpanded}
                  aria-label={isExpanded ? '접기' : '펼치기'}
                  title={isExpanded ? '접기' : '펼치기'}
                >
                  <span className="dept-tree-chevron" aria-hidden>
                    {isExpanded ? '▼' : '▶'}
                  </span>
                </button>
              ) : (
                <span className="dept-tree-toggle-placeholder" aria-hidden />
              )}
              <button
                type="button"
                className={`dept-tree-label dept-tree-label-button ${selectedDepartmentCode === code ? 'selected' : ''}`}
                onClick={() => onSelectDepartment?.(code)}
                aria-pressed={selectedDepartmentCode === code}
              >
                {departmentUiLabel(node.name)}
              </button>
              {canWrite && selectedDepartmentCode === code && code !== UNASSIGNED_DEPARTMENT_CODE && (
                <div className="user-management-v2-node-actions" role="group" aria-label="선택 부서 액션">
                  <button
                    type="button"
                    className="user-management-icon-btn"
                    onClick={() => onOpenAddDepartment?.(code, true)}
                    aria-label="하위 부서 추가"
                    title="하위 부서 추가"
                  >
                    <span aria-hidden>+</span>
                  </button>
                  <button
                    type="button"
                    className="user-management-icon-btn"
                    onClick={() => onOpenEditDepartment?.(code)}
                    aria-label="부서 수정"
                    title="부서 수정"
                  >
                    <span aria-hidden>E</span>
                  </button>
                  <button
                    type="button"
                    className="user-management-icon-btn"
                    onClick={() => onOpenAddUser?.(code)}
                    aria-label="사용자 추가"
                    title="사용자 추가"
                  >
                    <span aria-hidden>U</span>
                  </button>
                  <button
                    type="button"
                    className="user-management-icon-btn remove"
                    onClick={() => onOpenDeleteDepartment?.(code)}
                    aria-label="부서 삭제"
                    title="부서 삭제"
                  >
                    <span aria-hidden>X</span>
                  </button>
                </div>
              )}
            </div>
            {canExpand && isExpanded && (
              <>
                {hasUsers && (
                  <div className="hierarchy-node-users">
                    <table className="log-table hierarchy-users-table" aria-label={`${departmentUiLabel(node.name)} 사용자 목록`}>
                      <thead>
                        <tr>
                          <th scope="col">사용자명</th>
                          <th scope="col">사용자 ID</th>
                          <th scope="col">직급</th>
                          <th scope="col">직책</th>
                          <th scope="col">권한 그룹</th>
                          <th scope="col">작업</th>
                        </tr>
                      </thead>
                      <tbody>
                        {users.map((u) => renderUserRow(u, allGroups, onRefresh))}
                      </tbody>
                    </table>
                  </div>
                )}
                {!hasUsers && (
                  <p className="hierarchy-node-empty-users">해당 부서 사용자 없음</p>
                )}
                {hasChildren && (
                  <HierarchyTree
                    nodes={node.children}
                    expandedCodes={expandedCodes}
                    onToggle={onToggle}
                    level={level + 1}
                    isRoot={false}
                    renderUserRow={renderUserRow}
                    allGroups={allGroups}
                    onRefresh={onRefresh}
                    onSelectDepartment={onSelectDepartment}
                    selectedDepartmentCode={selectedDepartmentCode}
                    canWrite={canWrite}
                    onOpenAddDepartment={onOpenAddDepartment}
                    onOpenEditDepartment={onOpenEditDepartment}
                    onOpenAddUser={onOpenAddUser}
                    onOpenDeleteDepartment={onOpenDeleteDepartment}
                  />
                )}
              </>
            )}
          </li>
        );
      })}
    </ul>
  );
};

const UserManagement = ({ user }) => {
  const [tree, setTree] = useState([]);
  const [allGroups, setAllGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [expandedCodes, setExpandedCodes] = useState(() => new Set());
  const [departmentModalOpen, setDepartmentModalOpen] = useState(false);
  const [departmentModalMode, setDepartmentModalMode] = useState('child');
  const [v2TreeName, setV2TreeName] = useState('');
  const [v2TreeCode, setV2TreeCode] = useState('');
  const [v2TreeReason, setV2TreeReason] = useState('');
  const [v2TreeError, setV2TreeError] = useState(null);
  const [v2TreeSubmitting, setV2TreeSubmitting] = useState(false);
  /** Parent department code when opening "하위 부서 추가" — state for UI; ref is source of truth on submit (avoids stale closure / race). */
  const [departmentModalParentCode, setDepartmentModalParentCode] = useState(null);
  const departmentModalParentCodeRef = useRef(null);
  const [v2TreeEditDepartmentId, setV2TreeEditDepartmentId] = useState(null);
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [selectedDepartment, setSelectedDepartment] = useState(null);
  const [quickEntry, setQuickEntry] = useState({
    employeeNumber: emptyQuickEntryField,
    name: emptyQuickEntryField,
    rank: emptyQuickEntryField,
    permissionGroupId: emptyQuickEntryField,
  });
  const [lastEntry, setLastEntry] = useState({
    employeeNumber: '',
    name: '',
    rank: '',
    permissionGroupId: '',
  });
  const [v2Form, setV2Form] = useState({
    employeeNumber: '',
    name: '',
    rank: '',
    permissionGroupId: '',
    changeReason: '',
  });
  const [v2FormSubmitting, setV2FormSubmitting] = useState(false);
  const [v2FormError, setV2FormError] = useState(null);
  const [v2FormSuccess, setV2FormSuccess] = useState(null);
  const [deleteDepartmentModalOpen, setDeleteDepartmentModalOpen] = useState(false);
  const [deleteDepartmentReason, setDeleteDepartmentReason] = useState('');
  const [deleteDepartmentError, setDeleteDepartmentError] = useState(null);
  const [deleteDepartmentSubmitting, setDeleteDepartmentSubmitting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteReason, setDeleteReason] = useState('');
  const [deleteDialogError, setDeleteDialogError] = useState(null);
  const [deleteSubmitting, setDeleteSubmitting] = useState(false);
  const [filterDraft, setFilterDraft] = useState({
    departmentName: '',
    userName: '',
    employeeNumber: '',
  });
  /** Normalized lowercase applied tokens; null = no filter */
  const [appliedFilter, setAppliedFilter] = useState(null);

  const ids = getAllowedScreenIds(user);
  const screenFunctions = getScreenFunctions(user);
  const selfContext = useMemo(() => getSelfContextForDisplay(user), [user]);
  /** Effective read scope for UM v2 (API default team when omitted). System admin: all. */
  const effectiveUmV2Scope = useMemo(() => {
    if (user?.isSystemAdmin === true) return 'all';
    const raw =
      user?.screenScopes?.['user-management-v2'] ?? user?.screen_scopes?.['user-management-v2'];
    if (raw === 'self' || raw === 'team' || raw === 'all') return raw;
    return 'team';
  }, [user]);
  const isSelfScope = !user?.isSystemAdmin && effectiveUmV2Scope === 'self';

  const canAccessUserManagement =
    user?.isSystemAdmin === true ||
    (Array.isArray(ids) &&
      (ids.includes('user-management') ||
        ids.includes('user-permission-hierarchy') ||
        ids.includes('user-management-v2')));
  const canWrite =
    screenFunctions?.['user-management-v2']?.write === true ||
    screenFunctions?.['user-management']?.write === true ||
    screenFunctions?.['user-permission-hierarchy']?.write === true;

  const loadHierarchy = useCallback(async () => {
    if (!canAccessUserManagement) return;
    setLoading(true);
    setError(null);
    try {
      const [hierarchyRes, groupsRes] = await Promise.all([
        getUserPermissionHierarchy('tree'),
        listPermissionGroups(),
      ]);
      const hierarchyData = hierarchyRes.data;
      setTree(Array.isArray(hierarchyData) ? hierarchyData : (hierarchyData?.data || []));

      const groups = Array.isArray(groupsRes) ? groupsRes : (groupsRes?.data || []);
      setAllGroups(Array.isArray(groups) ? groups : []);
    } catch (e) {
      logger.error('사용자 관리 데이터 조회 실패:', e);
      setError(e?.status === 403 ? '관리자만 접근할 수 있습니다.' : getErrorMessage(e, '목록을 불러오지 못했습니다.'));
      setTree([]);
    } finally {
      setLoading(false);
    }
  }, [canAccessUserManagement]);

  const loadQuickEntry = useCallback(async () => {
    if (!canAccessUserManagement) return;
    try {
      const res = await getQuickEntryOptionsV2({
        fields: ['employeeNumber', 'name', 'rank', 'permissionGroupId'],
        limit: 10,
      });
      const data = res?.data || {};
      setQuickEntry({
        employeeNumber: toQuickEntryField(data.employeeNumber),
        name: toQuickEntryField(data.name),
        rank: toQuickEntryField(data.rank),
        permissionGroupId: toQuickEntryField(data.permissionGroupId),
      });
    } catch (e) {
      logger.warn('v2 quick entry options 조회 실패:', e);
      setQuickEntry({
        employeeNumber: emptyQuickEntryField,
        name: emptyQuickEntryField,
        rank: emptyQuickEntryField,
        permissionGroupId: emptyQuickEntryField,
      });
    }
  }, [canAccessUserManagement]);

  useEffect(() => {
    loadHierarchy();
  }, [loadHierarchy]);

  useEffect(() => {
    loadQuickEntry();
  }, [loadQuickEntry]);

  useEffect(() => {
    if (isSelfScope) {
      setAppliedFilter(null);
      setFilterDraft({ departmentName: '', userName: '', employeeNumber: '' });
    }
  }, [isSelfScope]);

  const nodeByCode = useMemo(() => {
    const map = new Map();
    const walk = (nodes) => {
      (nodes || []).forEach((n) => {
        if (n?.code) map.set(n.code, n);
        walk(n?.children || []);
      });
    };
    walk(tree);
    return map;
  }, [tree]);

  const displayTree = useMemo(() => {
    if (!appliedFilter) return tree;
    const { department, userName, employeeNumber } = appliedFilter;
    if (!department && !userName && !employeeNumber) return tree;
    return filterHierarchyNodes(tree, department, userName, employeeNumber);
  }, [tree, appliedFilter]);

  const treeFilterDisabled = loading || tree.length === 0;
  const filterActionsDisabled = treeFilterDisabled || isSelfScope;

  const handleFilterSearch = useCallback(
    (e) => {
      if (e?.preventDefault) e.preventDefault();
      const d = normalizeMatch(filterDraft.departmentName);
      const n = normalizeMatch(filterDraft.userName);
      const emp = normalizeMatch(filterDraft.employeeNumber);
      if (!d && !n && !emp) {
        setAppliedFilter(null);
        return;
      }
      setAppliedFilter({ department: d, userName: n, employeeNumber: emp });
      setExpandedCodes((prev) => {
        const next = new Set(prev);
        collectExpandCodesForMatches(tree, d, n, emp).forEach((c) => next.add(c));
        return next;
      });
    },
    [filterDraft, tree]
  );

  const handleFilterReset = useCallback(() => {
    setFilterDraft({ departmentName: '', userName: '', employeeNumber: '' });
    setAppliedFilter(null);
  }, []);

  const handleExpandAll = useCallback(() => {
    setExpandedCodes(collectAllExpandableCodes(tree));
  }, [tree]);

  const handleCollapseAll = useCallback(() => {
    setExpandedCodes(new Set());
  }, []);

  const handleToggle = (code) => {
    setExpandedCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  const handleSelectDepartmentByCode = (code) => {
    const node = nodeByCode.get(code);
    if (!node) return;
    setSelectedDepartment({
      // v2 contract: departmentId/parentDepartmentId are department code strings.
      departmentId: node.code ?? null,
      code: node.code,
      name: node.name != null ? String(node.name) : '',
    });
  };

  const openDeleteDialog = (u) => {
    const rowUserId = u.userId ?? u.username;
    const displayUserId = (u.employeeNumber ?? u.employee_number ?? '').toString().trim() || EMPLOYEE_NUMBER_FALLBACK_TEXT;
    const displayName = u.userName ?? rowUserId;
    setDeleteTarget({
      userId: rowUserId,
      displayName,
      displayUserId,
    });
    setDeleteReason('');
    setDeleteDialogError(null);
  };

  const closeDeleteDialog = () => {
    setDeleteTarget(null);
    setDeleteReason('');
    setDeleteDialogError(null);
    setDeleteSubmitting(false);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget?.userId) return;
    const trimmed = deleteReason.trim();
    if (!trimmed) {
      setDeleteDialogError('삭제 사유를 입력하세요.');
      return;
    }
    if (trimmed.length > USER_DELETE_CHANGE_REASON_MAX) {
      setDeleteDialogError(`사유는 ${USER_DELETE_CHANGE_REASON_MAX}자 이하여야 합니다.`);
      return;
    }
    setDeleteDialogError(null);
    setDeleteSubmitting(true);
    try {
      await deleteUser(deleteTarget.userId, { changeReason: trimmed });
      closeDeleteDialog();
      await loadHierarchy();
    } catch (e) {
      logger.error('사용자 삭제 실패:', e);
      setDeleteDialogError(getErrorMessage(e, '삭제에 실패했습니다.'));
    } finally {
      setDeleteSubmitting(false);
    }
  };

  const renderUserRow = (u, allGroups, onRefresh) => {
    const userId = u.userId ?? u.username;
    const displayUserId = (u.employeeNumber ?? u.employee_number ?? '').toString().trim() || EMPLOYEE_NUMBER_FALLBACK_TEXT;
    const displayName = u.userName ?? userId;
    const rank = u.rank ?? '-';
    const position = u.position ?? '-';
    const permissionGroups = u.permissionGroups || [];
    const isSystemAdmin = u.isSystemAdmin === true || u.is_system_admin === true;
    const canDeleteRow = canWrite && !isSystemAdmin;

    return (
      <tr key={userId}>
        <td>{displayName}</td>
        <td>
          {displayUserId}
          {isSystemAdmin && (
            <span className="system-admin-badge" aria-label="시스템 관리자">
              {' '}시스템 관리자
            </span>
          )}
        </td>
        <td>{rank}</td>
        <td>{position}</td>
        <td>
          <UserGroupAssignment
            userId={userId}
            userGroups={permissionGroups}
            allGroups={allGroups}
            onRefresh={onRefresh}
            disabled={!canWrite}
          />
        </td>
        <td>
          {canWrite && (
            <button
              type="button"
              className="user-management-btn remove"
              disabled={!canDeleteRow}
              title={
                isSystemAdmin
                  ? '시스템 관리자는 삭제할 수 없습니다.'
                  : '사용자 삭제'
              }
              aria-label={`${displayName} 사용자 삭제`}
              onClick={() => canDeleteRow && openDeleteDialog(u)}
            >
              삭제
            </button>
          )}
        </td>
      </tr>
    );
  };

  if (!canAccessUserManagement) {
    return (
      <div className="user-management">
        <h2>사용자 관리</h2>
        <p className="user-management-forbidden">관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  const departmentDialogTitleId = 'department-create-dialog-title';
  const createUserDialogTitleId = 'user-create-dialog-title';
  const deleteDepartmentDialogTitleId = 'department-delete-dialog-title';
  const deleteDialogTitleId = 'user-delete-dialog-title';

  const selectedPermissionGroupId = Number(v2Form.permissionGroupId);
  const canSubmitV2User =
    canWrite &&
    selectedDepartment?.departmentId != null &&
    v2Form.employeeNumber.trim() &&
    v2Form.name.trim() &&
    v2Form.rank.trim() &&
    Number.isFinite(selectedPermissionGroupId) &&
    selectedPermissionGroupId > 0 &&
    v2Form.changeReason.trim();

  const applyPreviousValue = (field) => {
    const value = lastEntry[field];
    if (value == null || String(value).trim() === '') return;
    setV2Form((prev) => ({ ...prev, [field]: String(value) }));
  };

  const applyRecentValue = (field, value) => {
    setV2Form((prev) => ({ ...prev, [field]: String(value ?? '') }));
  };

  const handleSubmitDepartmentModal = async (mode) => {
    const name = v2TreeName.trim();
    const code = v2TreeCode.trim();
    const reason = v2TreeReason.trim();
    const isEdit = mode === 'edit';
    const isChild = mode === 'child';
    if (!name) {
      setV2TreeError('부서명을 입력하세요.');
      return;
    }
    if (!isEdit && !code) {
      setV2TreeError('부서코드를 입력하세요.');
      return;
    }
    if (!reason) {
      setV2TreeError('변경 사유를 입력하세요.');
      return;
    }
    if (reason.length > V2_CHANGE_REASON_MAX) {
      setV2TreeError(`변경 사유는 ${V2_CHANGE_REASON_MAX}자 이하여야 합니다.`);
      return;
    }
    const parentCodeForChild = (() => {
      if (!isChild) return null;
      const fromRef = departmentModalParentCodeRef.current;
      if (fromRef != null && String(fromRef).trim() !== '') return String(fromRef).trim();
      if (departmentModalParentCode != null && String(departmentModalParentCode).trim() !== '') {
        return String(departmentModalParentCode).trim();
      }
      return null;
    })();
    if (isChild && parentCodeForChild == null) {
      setV2TreeError('하위 부서를 추가할 상위 부서를 먼저 선택하세요.');
      return;
    }
    if (isChild && parentCodeForChild === UNASSIGNED_DEPARTMENT_CODE) {
      setV2TreeError('미배치 그룹에는 하위 부서를 추가할 수 없습니다. 실제 부서를 선택하세요.');
      return;
    }
    if (isChild && !nodeByCode.has(parentCodeForChild)) {
      setV2TreeError('선택한 상위 부서가 트리에 없습니다. 목록을 새로고침한 뒤 다시 시도하세요.');
      return;
    }
    setV2TreeError(null);
    setV2TreeSubmitting(true);
    try {
      const body = {
        name,
        changeReason: reason,
      };
      if (isEdit) {
        await updateDepartmentV2(v2TreeEditDepartmentId, body);
      } else {
        body.code = code;
        await createChildDepartmentV2(parentCodeForChild, body);
      }
      setV2TreeName('');
      setV2TreeCode('');
      setV2TreeReason('');
      setV2TreeEditDepartmentId(null);
      setDepartmentModalOpen(false);
      departmentModalParentCodeRef.current = null;
      setDepartmentModalParentCode(null);
      await loadHierarchy();
      if (selectedDepartment?.code) {
        handleSelectDepartmentByCode(selectedDepartment.code);
      }
    } catch (e) {
      if (isEdit && (e?.status === 404 || e?.status === 405 || e?.status === 501)) {
        setV2TreeError('부서 수정 API가 아직 준비되지 않았습니다. 잠시 후 다시 시도하세요.');
      } else {
        setV2TreeError(getErrorMessage(e, isEdit ? '부서 수정에 실패했습니다.' : '부서 추가에 실패했습니다.'));
      }
    } finally {
      setV2TreeSubmitting(false);
    }
  };

  const handleSubmitDirectUser = async () => {
    if (!canSubmitV2User) {
      setV2FormError('필수 입력값을 확인하세요.');
      return;
    }
    if (v2Form.changeReason.trim().length > V2_CHANGE_REASON_MAX) {
      setV2FormError(`변경 사유는 ${V2_CHANGE_REASON_MAX}자 이하여야 합니다.`);
      return;
    }
    setV2FormSubmitting(true);
    setV2FormError(null);
    setV2FormSuccess(null);
    try {
      const payload = {
        departmentId: selectedDepartment.departmentId,
        employeeNumber: v2Form.employeeNumber.trim(),
        name: v2Form.name.trim(),
        rank: v2Form.rank.trim(),
        permissionGroupId: selectedPermissionGroupId,
        changeReason: v2Form.changeReason.trim(),
      };
      await createDirectUserV2(payload);
      setLastEntry({
        employeeNumber: payload.employeeNumber,
        name: payload.name,
        rank: payload.rank,
        permissionGroupId: String(payload.permissionGroupId),
      });
      setV2Form({
        employeeNumber: '',
        name: '',
        rank: payload.rank,
        permissionGroupId: String(payload.permissionGroupId),
        changeReason: '',
      });
      setQuickEntry((prev) => ({
        employeeNumber: {
          previous: payload.employeeNumber,
          recent: [payload.employeeNumber, ...prev.employeeNumber.recent.filter((v) => v !== payload.employeeNumber)].slice(0, 10),
        },
        name: {
          previous: payload.name,
          recent: [payload.name, ...prev.name.recent.filter((v) => v !== payload.name)].slice(0, 10),
        },
        rank: {
          previous: payload.rank,
          recent: [payload.rank, ...prev.rank.recent.filter((v) => v !== payload.rank)].slice(0, 10),
        },
        permissionGroupId: {
          previous: payload.permissionGroupId,
          recent: [payload.permissionGroupId, ...prev.permissionGroupId.recent.filter((v) => Number(v) !== payload.permissionGroupId)].slice(0, 10),
        },
      }));
      setV2FormSuccess('사용자가 등록되었습니다. 다음 등록을 계속 진행할 수 있습니다.');
      setUserModalOpen(false);
      await loadHierarchy();
    } catch (e) {
      setV2FormError(getErrorMessage(e, '사용자 등록에 실패했습니다.'));
    } finally {
      setV2FormSubmitting(false);
    }
  };

  const openDepartmentModal = (departmentCode, isChild) => {
    if (!isChild) return;
    const trimmedParent =
      departmentCode != null && String(departmentCode).trim() !== '' ? String(departmentCode).trim() : null;
    if (trimmedParent) {
      handleSelectDepartmentByCode(trimmedParent);
    }
    const parentForModal = trimmedParent;
    departmentModalParentCodeRef.current = parentForModal;
    setDepartmentModalParentCode(parentForModal);
    setDepartmentModalMode('child');
    setV2TreeName('');
    setV2TreeCode('');
    setV2TreeReason('');
    setV2TreeError(null);
    setV2TreeEditDepartmentId(null);
    setDepartmentModalOpen(true);
  };

  const openEditDepartmentModal = (departmentCode) => {
    const code = departmentCode || selectedDepartment?.code;
    if (!code) return;
    const node = nodeByCode.get(code);
    if (!node) return;
    handleSelectDepartmentByCode(code);
    departmentModalParentCodeRef.current = null;
    setDepartmentModalParentCode(null);
    setDepartmentModalMode('edit');
    setV2TreeName(node.name || '');
    setV2TreeCode(node.code || '');
    setV2TreeReason('');
    setV2TreeError(null);
    setV2TreeEditDepartmentId(node.code);
    setDepartmentModalOpen(true);
  };

  const openUserModal = (departmentCode) => {
    if (departmentCode) {
      handleSelectDepartmentByCode(departmentCode);
    }
    setV2FormError(null);
    setV2FormSuccess(null);
    setUserModalOpen(true);
  };

  const openDeleteDepartmentModal = (departmentCode) => {
    if (departmentCode) {
      handleSelectDepartmentByCode(departmentCode);
    }
    setDeleteDepartmentReason('');
    setDeleteDepartmentError(null);
    setDeleteDepartmentModalOpen(true);
  };

  const handleDeleteDepartment = async () => {
    const reason = deleteDepartmentReason.trim();
    if (!selectedDepartment?.departmentId) {
      setDeleteDepartmentError('삭제할 부서를 선택하세요.');
      return;
    }
    if (!reason) {
      setDeleteDepartmentError('변경 사유를 입력하세요.');
      return;
    }
    if (reason.length > V2_CHANGE_REASON_MAX) {
      setDeleteDepartmentError(`변경 사유는 ${V2_CHANGE_REASON_MAX}자 이하여야 합니다.`);
      return;
    }
    setDeleteDepartmentError(null);
    setDeleteDepartmentSubmitting(true);
    try {
      await deleteDepartmentV2(selectedDepartment.departmentId, { changeReason: reason });
      setDeleteDepartmentModalOpen(false);
      setSelectedDepartment(null);
      await loadHierarchy();
    } catch (e) {
      setDeleteDepartmentError(getErrorMessage(e, '부서 삭제에 실패했습니다.'));
    } finally {
      setDeleteDepartmentSubmitting(false);
    }
  };

  return (
    <div className="user-management">
      <div className="user-management-header">
        <h2>사용자 관리 v2</h2>
      </div>
      <p className="user-permission-hierarchy-hint">
        수동 부서 트리 편집과 직접 사용자 등록으로 계정을 관리합니다.
      </p>
      {error && (
        <div className="user-management-error" role="alert">
          {error}
        </div>
      )}

      <div className="sf-compact-panel user-management-v2-search-panel" aria-label="사용자 검색 필터">
        <form className="user-management-v2-filter-form" onSubmit={handleFilterSearch}>
          <fieldset
            className="user-management-v2-user-block-fieldset"
            disabled={treeFilterDisabled}
            aria-disabled={treeFilterDisabled}
          >
            <legend className="user-management-v2-user-block-legend">사용자</legend>
            {isSelfScope ? (
              <div
                className="user-management-v2-filter-toolbar-row"
                data-testid="um-v2-locked-self-block"
              >
                <div
                  className="user-management-v2-user-block-fields"
                  role="group"
                  aria-label="본인 조회 범위 고정 필드"
                >
                  <p className="user-permission-hierarchy-hint" id="um-v2-self-scope-hint">
                    조회 범위가 본인으로 고정되어 있습니다. 다른 사용자로 범위를 넓힐 수 없습니다.
                  </p>
                  <div className="form-group">
                    <label htmlFor="um-filter-dept-name-locked">부서명</label>
                    <input
                      id="um-filter-dept-name-locked"
                      type="text"
                      className="form-control"
                      readOnly
                      tabIndex={0}
                      value={selfContext?.department ?? ''}
                      aria-readonly="true"
                      aria-describedby="um-v2-self-scope-hint"
                      aria-label="부서명 (본인 고정)"
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="um-filter-user-name-locked">사용자명</label>
                    <input
                      id="um-filter-user-name-locked"
                      type="text"
                      className="form-control"
                      readOnly
                      tabIndex={0}
                      value={selfContext?.username ?? ''}
                      aria-readonly="true"
                      aria-describedby="um-v2-self-scope-hint"
                      aria-label="사용자명 (본인 고정)"
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="um-filter-userid-locked">사용자 ID (사번)</label>
                    <input
                      id="um-filter-userid-locked"
                      type="text"
                      className="form-control"
                      readOnly
                      tabIndex={0}
                      value={getEmployeeNumberDisplay(selfContext)}
                      aria-readonly="true"
                      aria-describedby="um-v2-self-scope-hint"
                      aria-label="사용자 ID (사번, 본인 고정)"
                    />
                  </div>
                </div>
              </div>
            ) : (
              <div className="user-management-v2-filter-toolbar-row">
                <div
                  className="user-management-v2-user-block-fields"
                  role="group"
                  aria-label="사용자 필터 필드"
                >
                  <div className="form-group">
                    <label htmlFor="um-filter-dept-name">부서명</label>
                    <input
                      id="um-filter-dept-name"
                      type="text"
                      className="form-control"
                      value={filterDraft.departmentName}
                      onChange={(ev) =>
                        setFilterDraft((p) => ({ ...p, departmentName: ev.target.value }))
                      }
                      placeholder="부서명 일부"
                      autoComplete="off"
                      aria-label="부서명 필터"
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="um-filter-user-name">사용자명</label>
                    <input
                      id="um-filter-user-name"
                      type="text"
                      className="form-control"
                      value={filterDraft.userName}
                      onChange={(ev) =>
                        setFilterDraft((p) => ({ ...p, userName: ev.target.value }))
                      }
                      placeholder="사용자명"
                      autoComplete="off"
                      aria-label="사용자명 필터"
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="um-filter-emp">사번</label>
                    <input
                      id="um-filter-emp"
                      type="text"
                      className="form-control"
                      value={filterDraft.employeeNumber}
                      onChange={(ev) =>
                        setFilterDraft((p) => ({ ...p, employeeNumber: ev.target.value }))
                      }
                      placeholder="사번"
                      autoComplete="off"
                      aria-label="사번 필터"
                    />
                  </div>
                </div>
                <div className="user-management-v2-filter-actions">
                  <button
                    type="submit"
                    className="btn btn-primary sf-btn"
                    disabled={filterActionsDisabled}
                  >
                    검색
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary sf-btn"
                    disabled={filterActionsDisabled}
                    onClick={handleFilterReset}
                  >
                    검색 초기화
                  </button>
                </div>
              </div>
            )}
          </fieldset>
        </form>
      </div>

      <div className="user-permission-hierarchy-layout">
        <section className="user-permission-hierarchy-tree-section" aria-label="부서별 사용자 계층">
          <div
            className="user-management-v2-tree-bulk-actions"
            role="group"
            aria-label="부서 트리 일괄 펼침·접기"
          >
            <Button
              type="button"
              size="small"
              variant="outlined"
              className="user-management-v2-tree-bulk-btn"
              disabled={treeFilterDisabled}
              onClick={handleExpandAll}
            >
              모두 펼치기
            </Button>
            <Button
              type="button"
              size="small"
              variant="outlined"
              className="user-management-v2-tree-bulk-btn"
              disabled={treeFilterDisabled}
              onClick={handleCollapseAll}
            >
              모두 접기
            </Button>
          </div>
          <div className="user-management-v2-tree-toolbar">
            <p className="user-management-v2-selected-dept">
              선택 부서:
              {' '}
              {selectedDepartment ? departmentUiLabel(selectedDepartment.name) : '미선택'}
            </p>
          </div>
          {loading ? (
            <p aria-live="polite">목록을 불러오는 중…</p>
          ) : tree.length === 0 ? (
            <p className="user-management-v2-tree-empty">등록된 부서가 없습니다.</p>
          ) : displayTree.length === 0 && appliedFilter ? (
            <p className="user-management-v2-filter-empty" role="status">
              조건에 맞는 부서 또는 사용자가 없습니다.
            </p>
          ) : (
            <HierarchyTree
              nodes={displayTree}
              expandedCodes={expandedCodes}
              onToggle={handleToggle}
              isRoot
              renderUserRow={renderUserRow}
              allGroups={allGroups}
              onRefresh={loadHierarchy}
              onSelectDepartment={handleSelectDepartmentByCode}
              selectedDepartmentCode={selectedDepartment?.code || null}
              canWrite={canWrite}
              onOpenAddDepartment={openDepartmentModal}
              onOpenEditDepartment={openEditDepartmentModal}
              onOpenAddUser={openUserModal}
              onOpenDeleteDepartment={openDeleteDepartmentModal}
            />
          )}
        </section>
      </div>

      {departmentModalOpen && (
        <Dialog
          open
          onClose={
            v2TreeSubmitting
              ? undefined
              : () => {
                  setDepartmentModalOpen(false);
                  departmentModalParentCodeRef.current = null;
                  setDepartmentModalParentCode(null);
                }
          }
          maxWidth="sm"
          fullWidth
          aria-labelledby={departmentDialogTitleId}
        >
          <DialogTitle id={departmentDialogTitleId}>
            {departmentModalMode === 'edit' ? '부서 수정' : '하위 부서 추가'}
          </DialogTitle>
          <DialogContent dividers>
            <TextField
              margin="dense"
              label="부서명"
              required
              fullWidth
              value={v2TreeName}
              onChange={(e) => setV2TreeName(e.target.value)}
            />
            <TextField
              margin="dense"
              label={departmentModalMode === 'edit' ? '부서코드' : '부서코드 *'}
              fullWidth
              value={v2TreeCode}
              onChange={(e) => setV2TreeCode(e.target.value)}
              required={departmentModalMode !== 'edit'}
              disabled={departmentModalMode === 'edit'}
            />
            <TextField
              margin="dense"
              label="변경 사유"
              required
              fullWidth
              multiline
              minRows={3}
              value={v2TreeReason}
              onChange={(e) => setV2TreeReason(e.target.value)}
            />
            {v2TreeError && <div className="user-management-error" role="alert">{v2TreeError}</div>}
          </DialogContent>
          <DialogActions>
            <Button
              type="button"
              onClick={() => {
                setDepartmentModalOpen(false);
                departmentModalParentCodeRef.current = null;
                setDepartmentModalParentCode(null);
              }}
              disabled={v2TreeSubmitting}
            >
              취소
            </Button>
            <Button
              type="button"
              variant="contained"
              onClick={() => handleSubmitDepartmentModal(departmentModalMode)}
              disabled={v2TreeSubmitting || !canWrite}
            >
              {v2TreeSubmitting ? '처리 중…' : '저장'}
            </Button>
          </DialogActions>
        </Dialog>
      )}

      {userModalOpen && (
        <Dialog
          open
          onClose={v2FormSubmitting ? undefined : () => setUserModalOpen(false)}
          maxWidth="md"
          fullWidth
          aria-labelledby={createUserDialogTitleId}
        >
          <DialogTitle id={createUserDialogTitleId}>사용자 추가</DialogTitle>
          <DialogContent dividers>
            <Typography component="p" variant="body2" sx={{ mb: 1 }}>
              대상 부서: {selectedDepartment ? departmentUiLabel(selectedDepartment.name) : '미선택'}
            </Typography>
            <div className="sf-row sf-block user-management-v2-user-form">
              <div>
                <label htmlFor="v2-employee-number">사번 *</label>
                <input id="v2-employee-number" className="sf-control" value={v2Form.employeeNumber} onChange={(e) => setV2Form((p) => ({ ...p, employeeNumber: e.target.value }))} />
                <div className="user-management-v2-quick-actions">
                  <button type="button" className="sf-btn" onClick={() => applyPreviousValue('employeeNumber')}>직전값 사용</button>
                  <select className="sf-control" aria-label="사번 최근값 선택" onChange={(e) => applyRecentValue('employeeNumber', e.target.value)} value="">
                    <option value="">최근값 선택</option>
                    {quickEntry.employeeNumber.recent.map((v) => <option key={`emp-recent-${v}`} value={v}>{String(v)}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label htmlFor="v2-name">이름 *</label>
                <input id="v2-name" className="sf-control" value={v2Form.name} onChange={(e) => setV2Form((p) => ({ ...p, name: e.target.value }))} />
                <div className="user-management-v2-quick-actions">
                  <button type="button" className="sf-btn" onClick={() => applyPreviousValue('name')}>직전값 사용</button>
                  <select className="sf-control" aria-label="이름 최근값 선택" onChange={(e) => applyRecentValue('name', e.target.value)} value="">
                    <option value="">최근값 선택</option>
                    {quickEntry.name.recent.map((v) => <option key={`name-recent-${v}`} value={v}>{String(v)}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label htmlFor="v2-rank">직급 *</label>
                <input id="v2-rank" className="sf-control" value={v2Form.rank} onChange={(e) => setV2Form((p) => ({ ...p, rank: e.target.value }))} />
                <div className="user-management-v2-quick-actions">
                  <button type="button" className="sf-btn" onClick={() => applyPreviousValue('rank')}>직전값 사용</button>
                  <select className="sf-control" aria-label="직급 최근값 선택" onChange={(e) => applyRecentValue('rank', e.target.value)} value="">
                    <option value="">최근값 선택</option>
                    {quickEntry.rank.recent.map((v) => <option key={`rank-recent-${v}`} value={v}>{String(v)}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label htmlFor="v2-permission">권한 그룹 *</label>
                <select id="v2-permission" className="sf-control" value={v2Form.permissionGroupId} onChange={(e) => setV2Form((p) => ({ ...p, permissionGroupId: e.target.value }))}>
                  <option value="">선택</option>
                  {allGroups.map((g) => <option key={`group-${g.id}`} value={String(g.id)}>{g.name || g.code || g.id}</option>)}
                </select>
                <div className="user-management-v2-quick-actions">
                  <button type="button" className="sf-btn" onClick={() => applyPreviousValue('permissionGroupId')}>직전값 사용</button>
                  <select className="sf-control" aria-label="권한 최근값 선택" onChange={(e) => applyRecentValue('permissionGroupId', e.target.value)} value="">
                    <option value="">최근값 선택</option>
                    {quickEntry.permissionGroupId.recent.map((v) => <option key={`perm-recent-${v}`} value={String(v)}>{String(v)}</option>)}
                  </select>
                </div>
              </div>
              <div className="user-management-v2-reason">
                <label htmlFor="v2-user-reason">등록 사유 *</label>
                <textarea
                  id="v2-user-reason"
                  className="sf-control"
                  rows={2}
                  maxLength={V2_CHANGE_REASON_MAX}
                  value={v2Form.changeReason}
                  onChange={(e) => setV2Form((p) => ({ ...p, changeReason: e.target.value }))}
                />
              </div>
            </div>
            {v2FormError && <div className="user-management-error" role="alert">{v2FormError}</div>}
            {v2FormSuccess && <div className="user-management-v2-success" role="status">{v2FormSuccess}</div>}
          </DialogContent>
          <DialogActions>
            <Button type="button" onClick={() => setUserModalOpen(false)} disabled={v2FormSubmitting}>취소</Button>
            <Button
              type="button"
              variant="contained"
              onClick={handleSubmitDirectUser}
              disabled={!canSubmitV2User || v2FormSubmitting}
            >
              {v2FormSubmitting ? '처리 중…' : '사용자 등록'}
            </Button>
          </DialogActions>
        </Dialog>
      )}

      {deleteDepartmentModalOpen && (
        <Dialog
          open
          onClose={deleteDepartmentSubmitting ? undefined : () => setDeleteDepartmentModalOpen(false)}
          maxWidth="sm"
          fullWidth
          aria-labelledby={deleteDepartmentDialogTitleId}
        >
          <DialogTitle id={deleteDepartmentDialogTitleId}>부서 삭제</DialogTitle>
          <DialogContent dividers>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              다음 부서를 삭제합니다. 하위 부서/사용자가 있으면 서버 정책에 따라 거부될 수 있습니다.
            </Typography>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              <strong>{selectedDepartment ? departmentUiLabel(selectedDepartment.name) : '-'}</strong>
            </Typography>
            <TextField
              label="변경 사유"
              required
              fullWidth
              multiline
              minRows={3}
              value={deleteDepartmentReason}
              onChange={(e) => setDeleteDepartmentReason(e.target.value)}
              inputProps={{ maxLength: V2_CHANGE_REASON_MAX }}
            />
            {deleteDepartmentError && <div className="user-management-error" role="alert">{deleteDepartmentError}</div>}
          </DialogContent>
          <DialogActions>
            <Button type="button" onClick={() => setDeleteDepartmentModalOpen(false)} disabled={deleteDepartmentSubmitting}>취소</Button>
            <Button type="button" color="error" variant="contained" onClick={handleDeleteDepartment} disabled={deleteDepartmentSubmitting || !canWrite}>
              {deleteDepartmentSubmitting ? '처리 중…' : '삭제'}
            </Button>
          </DialogActions>
        </Dialog>
      )}

      {deleteTarget && (
        <Dialog
          open
          onClose={deleteSubmitting ? undefined : closeDeleteDialog}
          maxWidth="sm"
          fullWidth
          aria-labelledby={deleteDialogTitleId}
        >
          <DialogTitle id={deleteDialogTitleId}>사용자 삭제</DialogTitle>
          <DialogContent dividers>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              다음 사용자를 삭제합니다. 되돌릴 수 없을 수 있습니다. 사유는 감사 로그에 남습니다.
            </Typography>
            <Typography component="p" variant="body2" sx={{ mb: 2 }}>
              <strong>{deleteTarget.displayName}</strong>
              {' '}
              <span aria-label="표시 사용자 ID">({String(deleteTarget.displayUserId)})</span>
            </Typography>
            {deleteDialogError && (
              <div className="user-management-error user-management-delete-dialog-error" role="alert">
                {deleteDialogError}
              </div>
            )}
            <TextField
              label="삭제 사유"
              placeholder="필수 입력"
              value={deleteReason}
              onChange={(ev) => setDeleteReason(ev.target.value)}
              multiline
              minRows={4}
              fullWidth
              required
              inputProps={{
                maxLength: USER_DELETE_CHANGE_REASON_MAX,
                'aria-required': true,
              }}
              helperText={`${deleteReason.trim().length}/${USER_DELETE_CHANGE_REASON_MAX} (공백만 불가, 전송 시 trim)`}
            />
          </DialogContent>
          <DialogActions>
            <Button type="button" onClick={closeDeleteDialog} disabled={deleteSubmitting}>
              취소
            </Button>
            <Button
              type="button"
              color="error"
              variant="contained"
              onClick={handleDeleteConfirm}
              disabled={deleteSubmitting}
            >
              {deleteSubmitting ? '처리 중…' : '삭제'}
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </div>
  );
};

export default UserManagement;
