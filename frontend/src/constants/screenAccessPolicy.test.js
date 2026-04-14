import { MENU_TREE } from './menuTree';
import {
  canAccessView,
  canShowAdminSidebarChild,
  canAccessUserManagementV2PocView,
  PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS,
  getVisibleAdminSidebarChildViews,
} from './screenAccessPolicy';

const adminChildByView = (view) => {
  const admin = MENU_TREE.find((n) => n.id === 'admin');
  return (admin?.children || []).find((c) => c.view === view);
};

describe('screenAccessPolicy (req 20260410 TC-04–TC-08)', () => {
  /** TC-04: matrix-only — v2 admin item visible; v1 same family gate */
  it('TC-04 matrix-only shows both permission-group v1 and v2 menu items', () => {
    const ids = ['permission-group-screen-matrix'];
    expect(canAccessView('permission-group-screen-matrix', { allowedScreenIds: ids })).toBe(true);
    expect(canAccessView('permission-group-management', { allowedScreenIds: ids })).toBe(true);
    const v2 = adminChildByView('permission-group-screen-matrix');
    const v1 = adminChildByView('permission-group-management');
    expect(
      canShowAdminSidebarChild(v2, {
        allowedScreenIds: ids,
        isAdmin: false,
        isHrSyncPocMenuEnabled: () => true,
      })
    ).toBe(true);
    expect(
      canShowAdminSidebarChild(v1, {
        allowedScreenIds: ids,
        isAdmin: false,
        isHrSyncPocMenuEnabled: () => true,
      })
    ).toBe(true);
  });

  /** TC-05: management-only — same family as matrix */
  it('TC-05 management-only shows permission-group v1 and v2 menu items', () => {
    const ids = ['permission-group-management'];
    expect(canAccessView('permission-group-management', { allowedScreenIds: ids })).toBe(true);
    expect(canAccessView('permission-group-screen-matrix', { allowedScreenIds: ids })).toBe(true);
  });

  /** TC-06: production UM v2 without PoC — PoC menu/guard must not pass */
  it('TC-06 hides PoC when user has user-management-v2 but not user-management-v2-poc', () => {
    const ids = ['user-management-v2'];
    expect(canAccessUserManagementV2PocView(ids)).toBe(false);
    expect(canAccessView('user-management-v2-poc', { allowedScreenIds: ids })).toBe(false);
    const pocChild = adminChildByView('user-management-v2-poc');
    expect(
      canShowAdminSidebarChild(pocChild, {
        allowedScreenIds: ids,
        isAdmin: false,
        isHrSyncPocMenuEnabled: () => true,
      })
    ).toBe(false);
  });

  /** TC-07: PoC revoked — no undocumented OR (production UM / hierarchy must not open PoC) */
  it('TC-07 PoC view denied when only production UM or hierarchy remains', () => {
    expect(canAccessUserManagementV2PocView(['user-management-v2'])).toBe(false);
    expect(canAccessUserManagementV2PocView(['user-management', 'user-permission-hierarchy'])).toBe(false);
    expect(canAccessUserManagementV2PocView(['hr-sync-poc'])).toBe(false);
    expect(canAccessView('user-management-v2-poc', { allowedScreenIds: ['user-management-v2'] })).toBe(false);
  });

  /** TC-08: MENU_TREE ⊆ ALLOWED_SCREEN_IDS, ORDERED_SCREEN_IDS, parity with ScreenConstants (see scripts/verify-screen-access-consistency.js) */
  it('TC-08 verifyMenuAndAllowlists has no errors', () => {
    // eslint-disable-next-line global-require, import/no-dynamic-require
    const { verifyMenuAndAllowlists } = require('../../../scripts/verify-screen-access-consistency.js');
    const { errors } = verifyMenuAndAllowlists();
    expect(errors).toEqual([]);
  });

  it('exports permission-groups API expectation list for drift script', () => {
    expect(Array.isArray(PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS)).toBe(true);
    expect(PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS).toContain('permission-group-screen-matrix');
    expect(PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS).toContain('permission-group-management');
  });
});

const pocOff = () => false;
const pocOn = () => true;

describe('screenAccessPolicy — req 20260414 (matrix rows vs 관리 메뉴)', () => {
  /** TC-02: three matrix ids in the PG family still yield multiple sidebar leaves (alias expansion is documented). */
  it('TC-02: three PG-family screen ids can produce more than three 관리 menu leaves', () => {
    const ids = [
      'user-permission-hierarchy',
      'permission-group-management',
      'permission-group-screen-matrix',
    ];
    const visible = getVisibleAdminSidebarChildViews(ids, {
      isSystemAdmin: false,
      isHrSyncPocMenuEnabled: pocOff,
    });
    expect(ids.length).toBe(3);
    expect(visible.length).toBeGreaterThan(3);
    expect(visible).toEqual(
      expect.arrayContaining([
        'user-management',
        'user-management-v2',
        'permission-group-management',
        'permission-group-screen-matrix',
      ])
    );
  });

  /** TC-03: hierarchy alone unlocks PG v1+v2 and legacy UM paths per family / UM alias rules */
  it('TC-03: user-permission-hierarchy alone unlocks PG v1, v2, legacy UM, and UM v2 menu items', () => {
    const ids = ['user-permission-hierarchy'];
    const visible = getVisibleAdminSidebarChildViews(ids, {
      isSystemAdmin: false,
      isHrSyncPocMenuEnabled: pocOff,
    });
    expect(visible).toEqual(
      expect.arrayContaining([
        'user-management',
        'user-management-v2',
        'permission-group-management',
        'permission-group-screen-matrix',
      ])
    );
    expect(visible).not.toContain('user-management-v2-poc');
  });

  /** TC-06 (§3): system admin sees all 관리 leaves (except PoC entries when PoC UI off) */
  it('TC-06: isSystemAdmin yields every non-PoC 관리 leaf including screen-display-labels', () => {
    const visible = getVisibleAdminSidebarChildViews([], {
      isSystemAdmin: true,
      isHrSyncPocMenuEnabled: pocOff,
    });
    expect(visible).toContain('screen-display-labels');
    expect(visible).toContain('permission-group-screen-matrix');
    expect(visible).not.toContain('hr-sync-poc');
    expect(visible).not.toContain('user-management-v2-poc');
  });

  it('TC-06b: PoC menu entries appear when HR Sync PoC UI is enabled', () => {
    const visible = getVisibleAdminSidebarChildViews([], {
      isSystemAdmin: true,
      isHrSyncPocMenuEnabled: pocOn,
    });
    expect(visible).toEqual(expect.arrayContaining(['hr-sync-poc', 'user-management-v2-poc']));
  });
});
