/**
 * Normalized allowedScreens helpers aligned with PermissionGroupPanel + ScreenSelectionTree.
 * Duplicated from PermissionGroupPanel (no shared import) so matrix stays API-consistent.
 */
import {
  SCREENS_WITH_WRITE,
  SCREENS_WITH_APPROVE,
  SCREENS_WITH_DECRYPT,
} from '../../constants/screenFunctionDescriptions';

/** Screens where approval scope is fixed to team when approve=true. */
export const APPROVAL_SCOPE_FIXED_SCREENS = ['search-history', 'pending-approvals'];

/** Scope dropdown applies to these screens (same as ScreenSelectionTree). */
export const SCOPE_SUPPORTING_SCREENS = [
  'activity-log',
  'statistics',
  'search-history',
  'pending-approvals',
  'user-management-v2',
];

/** Default scope when enabling a screen in the matrix (matches ScreenSelectionTree toggle). */
export const DEFAULT_SCOPE_ON_ADD = 'team';

/** Legacy + Unicode hyphen normalization (req 20260318-permission-group-menu-invalid-screen-id-imagelog). */
export const normalizeScreenId = (id) => {
  if (id == null || id === '') return id;
  let s = String(id).trim();
  s = s.replace(/[\u200B-\u200D\uFEFF]/g, '');
  s = s.replace(/[\u2010-\u2015\u2212\uFE58\uFE63\uFF0D]/g, '-');
  if (s === 'java-fw_imagelog') return 'java-fw-imagelog';
  return s;
};

/** Default scope when API omits scope (align PermissionGroupPanel; UM v2 default team). */
const defaultScopeWhenMissing = (screenId) => (screenId === 'user-management-v2' ? 'team' : 'self');

/**
 * Normalize allowedScreens to [{ screenId, scope?, read?, write?, approve?, decrypt? }].
 * Same rules as PermissionGroupPanel.normalizeAllowedScreens.
 */
export const normalizeAllowedScreens = (arr) => {
  const scopeScreens = [
    'activity-log',
    'statistics',
    'search-history',
    'pending-approvals',
    'user-management-v2',
  ];
  const decryptScreens = SCREENS_WITH_DECRYPT;
  if (!Array.isArray(arr)) return [];
  return arr.map((s) => {
    const rawId = typeof s === 'string' ? s : s.screenId;
    const screenId = normalizeScreenId(rawId);
    const base = typeof s === 'string'
      ? { screenId, scope: scopeScreens.includes(screenId) ? defaultScopeWhenMissing(screenId) : undefined }
      : {
          screenId,
          scope: s.scope || (scopeScreens.includes(screenId) ? defaultScopeWhenMissing(screenId) : undefined),
          read: s.read,
          write: s.write,
          approve: s.approve,
          decrypt: s.decrypt,
        };
    const hasWrite = SCREENS_WITH_WRITE.includes(base.screenId);
    const hasApprove = SCREENS_WITH_APPROVE.includes(base.screenId);
    const hasDecrypt = decryptScreens.includes(base.screenId);
    const approved = base.approve ?? (hasApprove ? false : undefined);
    const decryptVal = base.decrypt ?? (hasDecrypt ? false : undefined);
    let scope = base.scope ?? (scopeScreens.includes(base.screenId) ? defaultScopeWhenMissing(base.screenId) : undefined);
    if (approved === true && APPROVAL_SCOPE_FIXED_SCREENS.includes(base.screenId)) {
      scope = 'team';
    }
    return {
      ...base,
      scope,
      read: base.read ?? true,
      write: base.write ?? (hasWrite ? true : undefined),
      approve: approved ?? (hasApprove ? false : undefined),
      decrypt: decryptVal ?? (hasDecrypt ? false : undefined),
    };
  });
};

/** Build API payload for allowedScreens (same as PermissionGroupPanel). */
export const toAllowedScreensPayload = (screens) =>
  screens.map((s) => {
    const item = { screenId: s.screenId };
    let scope = s.scope;
    if (s.approve === true && APPROVAL_SCOPE_FIXED_SCREENS.includes(s.screenId)) {
      scope = 'team';
    }
    if (scope) item.scope = scope;
    if (s.read !== undefined) item.read = s.read;
    if (s.write !== undefined) item.write = s.write;
    if (s.approve !== undefined) item.approve = s.approve;
    if (s.decrypt !== undefined) item.decrypt = s.decrypt;
    return item;
  });

/** Flatten MENU_TREE children in menu order (same screens as ScreenSelectionTree). */
export const flattenMenuTreeToRows = (menuTree) => {
  const rows = [];
  menuTree.forEach((node) => {
    (node.children || []).forEach((child) => {
      rows.push({
        order: rows.length,
        groupId: node.id,
        groupLabel: node.label,
        screenId: child.view,
        screenLabel: child.label,
        childId: child.id,
      });
    });
  });
  return rows;
};

/** New row when user checks "메뉴·API 사용" (aligned with ScreenSelectionTree.toggleScreen). */
export const createAllowedEntryForScreen = (screenId) => {
  const hasWrite = SCREENS_WITH_WRITE.includes(screenId);
  const hasApprove = SCREENS_WITH_APPROVE.includes(screenId);
  const hasDecrypt = SCREENS_WITH_DECRYPT.includes(screenId);
  const scope = SCOPE_SUPPORTING_SCREENS.includes(screenId) ? DEFAULT_SCOPE_ON_ADD : undefined;
  return normalizeAllowedScreens([
    {
      screenId,
      scope,
      read: true,
      write: hasWrite ? true : undefined,
      approve: hasApprove ? false : undefined,
      decrypt: hasDecrypt ? false : undefined,
    },
  ])[0];
};

export const SCOPE_OPTIONS = [
  { value: 'self', label: '본인' },
  { value: 'team', label: '부서' },
  { value: 'all', label: '전체' },
];
