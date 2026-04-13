import { MENU_TREE } from '../constants/menuTree';
import {
  mergeMenuLabels,
  applyLogTypeLabelOverrides,
  labelItemsToUserLabelMap,
  buildMergedMenuTree,
} from './mergeMenuLabels';

describe('mergeMenuLabels', () => {
  it('returns same reference when API items empty', () => {
    expect(mergeMenuLabels(MENU_TREE, [])).toBe(MENU_TREE);
    expect(mergeMenuLabels(MENU_TREE, null)).toBe(MENU_TREE);
  });

  it('overrides leaf label by screenId (view)', () => {
    const items = [{ screenId: 'pb-feplog', labelUser: 'PB FEP (merged)' }];
    const merged = mergeMenuLabels(MENU_TREE, items);
    expect(merged).not.toBe(MENU_TREE);
    const logSearch = merged.find((n) => n.id === 'log-search');
    const leaf = logSearch.children.find((c) => c.view === 'pb-feplog');
    expect(leaf.label).toBe('PB FEP (merged)');
    const other = logSearch.children.find((c) => c.view === 'pb-fep-log-search');
    expect(other.label).toBe('PB FEP v2.0.0');
  });

  it('applies partial overrides only for provided screenIds', () => {
    const items = [
      { screenId: 'pb-fep-log-search', labelUser: '와이어프레임 검색' },
      { screenId: 'activity-log', labelUser: '감사 로그' },
    ];
    const merged = mergeMenuLabels(MENU_TREE, items);
    const logSearch = merged.find((n) => n.id === 'log-search');
    expect(logSearch.children.find((c) => c.view === 'pb-fep-log-search').label).toBe('와이어프레임 검색');
    const history = merged.find((n) => n.id === 'history');
    expect(history.children.find((c) => c.view === 'activity-log').label).toBe('감사 로그');
    expect(history.children.find((c) => c.view === 'search-history').label).toBe('검색 이력');
  });
});

describe('labelItemsToUserLabelMap', () => {
  it('skips blank labelUser', () => {
    const m = labelItemsToUserLabelMap([{ screenId: 'pb-feplog', labelUser: '   ' }]);
    expect(m.has('pb-feplog')).toBe(false);
  });
});

describe('buildMergedMenuTree', () => {
  it('returns same reference when API items empty', () => {
    expect(buildMergedMenuTree(MENU_TREE, [])).toBe(MENU_TREE);
  });

  it('moves search-history under log-search and removes it from history', () => {
    const items = [{ screenId: 'search-history', parentGroupId: 'log-search', sortOrder: 0 }];
    const merged = buildMergedMenuTree(MENU_TREE, items);
    const logSearch = merged.find((n) => n.id === 'log-search');
    const history = merged.find((n) => n.id === 'history');
    expect(logSearch.children.map((c) => c.view)).toContain('search-history');
    expect(history.children.map((c) => c.view)).not.toContain('search-history');
  });

  it('orders activity-log first in history when sortOrder 0 and siblings higher', () => {
    const items = [
      { screenId: 'activity-log', parentGroupId: 'history', sortOrder: 0 },
      { screenId: 'search-history', parentGroupId: 'history', sortOrder: 5 },
      { screenId: 'pending-approvals', parentGroupId: 'history', sortOrder: 10 },
    ];
    const merged = buildMergedMenuTree(MENU_TREE, items);
    const history = merged.find((n) => n.id === 'history');
    expect(history.children.map((c) => c.view)).toEqual([
      'activity-log',
      'search-history',
      'pending-approvals',
    ]);
  });

  it('tie-breaks same sortOrder by screenId ascending', () => {
    const items = [
      { screenId: 'activity-log', parentGroupId: 'history', sortOrder: 1 },
      { screenId: 'search-history', parentGroupId: 'history', sortOrder: 1 },
    ];
    const merged = buildMergedMenuTree(MENU_TREE, items);
    const history = merged.find((n) => n.id === 'history');
    const pos = (v) => history.children.findIndex((c) => c.view === v);
    expect(pos('activity-log')).toBeLessThan(pos('search-history'));
  });

  it('ignores invalid parentGroupId and keeps MENU_TREE default', () => {
    const items = [{ screenId: 'activity-log', parentGroupId: 'unknown-group', sortOrder: 0 }];
    const merged = buildMergedMenuTree(MENU_TREE, items);
    const history = merged.find((n) => n.id === 'history');
    expect(history.children.map((c) => c.view)).toContain('activity-log');
  });

  it('applies labelUser like merge when structure unchanged', () => {
    const items = [{ screenId: 'pb-feplog', labelUser: 'Merged title' }];
    const merged = buildMergedMenuTree(MENU_TREE, items);
    const logSearch = merged.find((n) => n.id === 'log-search');
    expect(logSearch.children.find((c) => c.view === 'pb-feplog').label).toBe('Merged title');
  });
});

describe('applyLogTypeLabelOverrides', () => {
  const defaults = {
    'pb-feplog': { id: 'pb_feplog', name: 'PB FEP v1.0.0', description: '' },
    'pb-fep-log-search': { id: 'pb_feplog', name: 'PB FEP v2.0.0', description: '' },
    'java-fw-imagelog': { id: 'java_fw_imglog', name: 'Java FW Image Log', description: '' },
  };

  it('updates name for log views when API provides labelUser', () => {
    const items = [
      { screenId: 'pb-feplog', labelUser: 'Title A' },
      { screenId: 'java-fw-imagelog', labelUser: 'Title B' },
    ];
    const out = applyLogTypeLabelOverrides(defaults, items);
    expect(out['pb-feplog'].name).toBe('Title A');
    expect(out['java-fw-imagelog'].name).toBe('Title B');
    expect(out['pb-fep-log-search'].name).toBe('PB FEP v2.0.0');
    expect(out['pb-feplog'].id).toBe('pb_feplog');
  });
});
