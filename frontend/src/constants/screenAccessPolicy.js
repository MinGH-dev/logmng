/**
 * Central screen access policy: documented alias/OR rules only (no ad-hoc OR in AppSidebar / App / panels).
 *
 * @see docs/requirements/20260410-screen-access-menu-api-consistency.md
 */

/** Screen ids the product treats as satisfying `/api/permission-groups.*` (must match ScreenAccessInterceptor when backend is aligned). */
export const PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS = [
  'user-management',
  'user-permission-hierarchy',
  'user-management-v2',
  'permission-group-management',
  'permission-group-screen-matrix',
];

/**
 * Documented alias: legacy `user-management` and `user-permission-hierarchy` grant the same legacy UM shell.
 * Ref: docs/requirements/20260410-screen-access-menu-api-consistency.md (permission-group-hierarchy / UM family).
 */
export function hasUserManagementLegacyAlias(ids) {
  if (!Array.isArray(ids)) return false;
  return ids.includes('user-management') || ids.includes('user-permission-hierarchy');
}

/**
 * Documented alias: production UM v2 also accepts legacy UM / hierarchy screens for the same management area.
 * Ref: docs/requirements/20260410-screen-access-menu-api-consistency.md §2.
 */
export function canAccessUserManagementV2View(ids) {
  if (!Array.isArray(ids)) return false;
  return ids.includes('user-management-v2') || hasUserManagementLegacyAlias(ids);
}

/**
 * Strict PoC UM v2: only `user-management-v2-poc` grants the PoC view/menu (no fallback to production UM or hierarchy).
 * Ref: docs/requirements/20260410-screen-access-menu-api-consistency.md §1 TC-06, TC-07.
 */
export function canAccessUserManagementV2PocView(ids) {
  if (!Array.isArray(ids)) return false;
  return ids.includes('user-management-v2-poc');
}

/**
 * HR Sync PoC preview route uses the same documented gate as legacy UM (not the PoC UM v2 clone).
 * Ref: docs/requirements/20260410-screen-access-menu-api-consistency.md (HR Sync PoC sidebar).
 */
export function canAccessHrSyncPocView(ids) {
  return hasUserManagementLegacyAlias(ids);
}

/**
 * Permission-group admin family: v1 management, v2 matrix, or hierarchy admin.
 * Ref: docs/requirements/20260410-screen-access-menu-api-consistency.md §1 (matrix honored with management).
 */
export function hasPermissionGroupAdminFamilyAccess(ids) {
  if (!Array.isArray(ids)) return false;
  return (
    ids.includes('permission-group-management') ||
    ids.includes('permission-group-screen-matrix') ||
    ids.includes('user-permission-hierarchy')
  );
}

/**
 * Write for permission-group UIs: any of the family screens may carry the write bit.
 * Ref: docs/requirements/20260410-screen-access-menu-api-consistency.md §2 Frontend.
 */
export function hasPermissionGroupAdminWrite(screenFunctions) {
  if (!screenFunctions || typeof screenFunctions !== 'object') return false;
  return (
    screenFunctions['permission-group-management']?.write === true ||
    screenFunctions['permission-group-screen-matrix']?.write === true ||
    screenFunctions['user-permission-hierarchy']?.write === true
  );
}

/**
 * @param {string} view - MENU_TREE child `view` id
 * @param {{ allowedScreenIds: string[], isSystemAdmin?: boolean }} ctx
 */
export function canAccessView(view, ctx) {
  const { allowedScreenIds: idsRaw, isSystemAdmin } = ctx || {};
  if (isSystemAdmin === true) return true;
  const ids = Array.isArray(idsRaw) ? idsRaw : [];
  if (ids.length === 0) return false;

  if (view === 'user-management' || view === 'user-permission-hierarchy') {
    return hasUserManagementLegacyAlias(ids);
  }
  if (view === 'hr-sync-poc') {
    return canAccessHrSyncPocView(ids);
  }
  if (view === 'user-management-v2') {
    return canAccessUserManagementV2View(ids);
  }
  if (view === 'user-management-v2-poc') {
    return canAccessUserManagementV2PocView(ids);
  }
  if (view === 'permission-group-management' || view === 'permission-group-screen-matrix') {
    return hasPermissionGroupAdminFamilyAccess(ids);
  }
  return ids.includes(view);
}

/**
 * Sidebar admin child visibility (PoC menu flags applied by caller before this for HR / PoC items).
 *
 * @param {{ view?: string, systemAdminOnly?: boolean }} child - MENU_TREE child
 * @param {{ allowedScreenIds: string[], isAdmin: boolean, isHrSyncPocMenuEnabled: () => boolean }} ctx
 */
export function canShowAdminSidebarChild(child, ctx) {
  const { allowedScreenIds: idsRaw, isAdmin, isHrSyncPocMenuEnabled } = ctx || {};
  if (child?.systemAdminOnly === true && !isAdmin) return false;
  if (child?.view === 'hr-sync-poc' && typeof isHrSyncPocMenuEnabled === 'function' && !isHrSyncPocMenuEnabled()) {
    return false;
  }
  if (child?.view === 'user-management-v2-poc' && typeof isHrSyncPocMenuEnabled === 'function' && !isHrSyncPocMenuEnabled()) {
    return false;
  }
  const ids = Array.isArray(idsRaw) ? idsRaw : [];
  if (!child?.view || !ids.length) return false;
  return canAccessView(child.view, { allowedScreenIds: ids, isSystemAdmin: false });
}

/**
 * Whether current session may stay on / navigate to `currentView` (non–system-admin).
 */
export function canNonAdminAccessCurrentView(currentView, allowedScreenIds) {
  const ids = Array.isArray(allowedScreenIds) ? allowedScreenIds : [];
  return canAccessView(currentView, { allowedScreenIds: ids, isSystemAdmin: false });
}

export function canAccessDeepLinkHrSyncPoc({ allowedScreenIds, isSystemAdmin }) {
  if (isSystemAdmin === true) return true;
  return canAccessHrSyncPocView(allowedScreenIds);
}

export function canAccessDeepLinkUserManagementV2Poc({ allowedScreenIds, isSystemAdmin }) {
  if (isSystemAdmin === true) return true;
  return canAccessUserManagementV2PocView(allowedScreenIds);
}
