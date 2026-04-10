import { ALLOWED_SCREEN_IDS, MENU_TREE, ORDERED_SCREEN_IDS } from './menuTree';

describe('menuTree permission allowlists (req 20260406 screen-display-labels)', () => {
  test('ALLOWED_SCREEN_IDS includes grantable screen-display-labels after permission-group-management', () => {
    expect(ALLOWED_SCREEN_IDS).toContain('screen-display-labels');
    const i = ALLOWED_SCREEN_IDS.indexOf('screen-display-labels');
    const j = ALLOWED_SCREEN_IDS.indexOf('permission-group-management');
    expect(j).toBeGreaterThanOrEqual(0);
    expect(i).toBeGreaterThan(j);
  });

  test('ORDERED_SCREEN_IDS includes screen-display-labels for first-screen resolution (TC-03)', () => {
    expect(ORDERED_SCREEN_IDS).toContain('screen-display-labels');
    expect(ORDERED_SCREEN_IDS[ORDERED_SCREEN_IDS.length - 1]).toBe('screen-display-labels');
  });

  test('MENU_TREE admin leaf includes screen-display-labels for matrix rows', () => {
    const admin = MENU_TREE.find((n) => n.id === 'admin');
    const views = (admin?.children || []).map((c) => c.view);
    expect(views).toContain('screen-display-labels');
  });

  test('TC-05 regression: core screen ids remain in ordered allowlist', () => {
    for (const id of [
      'java-fw-imagelog',
      'activity-log',
      'permission-group-management',
      'permission-group-screen-matrix',
    ]) {
      expect(ORDERED_SCREEN_IDS).toContain(id);
    }
  });
});
