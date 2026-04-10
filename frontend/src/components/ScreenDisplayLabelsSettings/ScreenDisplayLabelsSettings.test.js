import {
  initialRowsFromLabelItems,
  applySortOrderWithinGroups,
  parseSortOrderFromApi,
} from './ScreenDisplayLabelsSettings';
import { getDefaultSortOrderForScreenId } from '../../constants/menuTree';

describe('initialRowsFromLabelItems', () => {
  it('uses MENU_TREE default parent when API omits parentGroupId (e.g. activity-log → history)', () => {
    const items = [
      {
        screenId: 'activity-log',
        labelUser: '활동 이력',
        // no parentGroupId
      },
    ];
    const rows = initialRowsFromLabelItems(items, true);
    const row = rows.find((r) => r.screenId === 'activity-log');
    expect(row).toBeDefined();
    expect(row.parentGroupId).toBe('history');
  });

  it('keeps empty parentGroupId for screen ids not in MENU_TREE leaves (e.g. main)', () => {
    const items = [{ screenId: 'main', labelUser: '메인' }];
    const rows = initialRowsFromLabelItems(items, true);
    const row = rows.find((r) => r.screenId === 'main');
    expect(row.parentGroupId).toBe('');
  });

  it('prefers API parentGroupId when valid', () => {
    const items = [
      {
        screenId: 'activity-log',
        labelUser: 'X',
        parentGroupId: 'statistics',
      },
    ];
    const rows = initialRowsFromLabelItems(items, true);
    expect(rows.find((r) => r.screenId === 'activity-log').parentGroupId).toBe('statistics');
  });

  it('falls back to MENU_TREE default when API parentGroupId is invalid', () => {
    const items = [
      {
        screenId: 'activity-log',
        labelUser: 'X',
        parentGroupId: 'not-a-real-group',
      },
    ];
    const rows = initialRowsFromLabelItems(items, true);
    expect(rows.find((r) => r.screenId === 'activity-log').parentGroupId).toBe('history');
  });

  it('parses sortOrder from API when it is a string (e.g. JSON edge case)', () => {
    const items = [
      {
        screenId: 'activity-log',
        labelUser: '활동 이력',
        sortOrder: '2',
      },
    ];
    const rows = initialRowsFromLabelItems(items, true);
    expect(rows.find((r) => r.screenId === 'activity-log').sortOrder).toBe(2);
  });

  it('uses MENU_TREE default sortOrder when API omits sortOrder', () => {
    const items = [
      {
        screenId: 'activity-log',
        labelUser: '활동 이력',
      },
    ];
    const expected = getDefaultSortOrderForScreenId('activity-log');
    const rows = initialRowsFromLabelItems(items, true);
    expect(rows.find((r) => r.screenId === 'activity-log').sortOrder).toBe(expected);
  });
});

describe('parseSortOrderFromApi', () => {
  it('returns default when sortOrder is missing', () => {
    const def = getDefaultSortOrderForScreenId('activity-log');
    expect(parseSortOrderFromApi({ screenId: 'activity-log', labelUser: 'x' }, 'activity-log')).toBe(
      def
    );
    expect(parseSortOrderFromApi(null, 'activity-log')).toBe(def);
  });

  it('parses non-negative numeric string', () => {
    expect(parseSortOrderFromApi({ sortOrder: '2' }, 'activity-log')).toBe(2);
  });
});

describe('applySortOrderWithinGroups', () => {
  const base = [
    { screenId: 'a', parentGroupId: 'history', sortOrder: 0, labelUser: '', labelAdmin: '' },
    { screenId: 'b', parentGroupId: 'history', sortOrder: 1, labelUser: '', labelAdmin: '' },
    { screenId: 'c', parentGroupId: 'log-search', sortOrder: 0, labelUser: '', labelAdmin: '' },
  ];

  it('sets sortOrder 0..n-1 for the given group only', () => {
    const out = applySortOrderWithinGroups(base, 'history', ['b', 'a']);
    expect(out.find((r) => r.screenId === 'a').sortOrder).toBe(1);
    expect(out.find((r) => r.screenId === 'b').sortOrder).toBe(0);
    expect(out.find((r) => r.screenId === 'c').sortOrder).toBe(0);
  });

  it('handles empty-string parent group (기타)', () => {
    const rows = [
      ...base,
      { screenId: 'main', parentGroupId: '', sortOrder: 0, labelUser: '', labelAdmin: '' },
      { screenId: 'x', parentGroupId: '', sortOrder: 1, labelUser: '', labelAdmin: '' },
    ];
    const out = applySortOrderWithinGroups(rows, '', ['x', 'main']);
    expect(out.find((r) => r.screenId === 'main').sortOrder).toBe(1);
    expect(out.find((r) => r.screenId === 'x').sortOrder).toBe(0);
  });
});
