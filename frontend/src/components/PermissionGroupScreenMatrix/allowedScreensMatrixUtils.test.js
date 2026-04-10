import {
  createAllowedEntryForScreen,
  flattenMenuTreeToRows,
  toAllowedScreensPayload,
} from './allowedScreensMatrixUtils';
import { MENU_TREE } from '../../constants/menuTree';

describe('allowedScreensMatrixUtils (screen-display-labels save payload)', () => {
  test('flatten includes screen-display-labels row from MENU_TREE', () => {
    const rows = flattenMenuTreeToRows(MENU_TREE);
    const row = rows.find((r) => r.screenId === 'screen-display-labels');
    expect(row).toMatchObject({
      screenId: 'screen-display-labels',
      screenLabel: '화면 표시 이름',
    });
  });

  test('createAllowedEntry + payload keeps screen-display-labels (read-only; no write flag)', () => {
    const entry = createAllowedEntryForScreen('screen-display-labels');
    expect(entry.screenId).toBe('screen-display-labels');
    expect(entry.read).toBe(true);
    expect(entry.write).toBeUndefined();
    const payload = toAllowedScreensPayload([entry]);
    expect(payload).toEqual([{ screenId: 'screen-display-labels', read: true }]);
  });
});
