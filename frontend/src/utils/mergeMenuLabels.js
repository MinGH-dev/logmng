/**
 * Merge server-provided screen display labels over MENU_TREE defaults (req 20260406-menu-display-names-admin).
 * Keys match leaf `view` (and `id` when id === view). React text nodes only — no HTML.
 */

import { PARENT_GROUP_IDS } from '../constants/menuTree';

const VALID_PARENT_GROUP = new Set(PARENT_GROUP_IDS);

/**
 * Build a map screenId -> labelUser from API items.
 * @param {Array<{ screenId?: string, labelUser?: string }>} items
 * @returns {Map<string, string>}
 */
export function labelItemsToUserLabelMap(items) {
  const map = new Map();
  if (!Array.isArray(items)) return map;
  items.forEach((it) => {
    if (!it || typeof it.screenId !== 'string' || it.screenId === '') return;
    if (typeof it.labelUser === 'string' && it.labelUser.trim() !== '') {
      map.set(it.screenId.trim(), it.labelUser.trim());
    }
  });
  return map;
}

/**
 * Deep-merge labelUser overrides into a menu tree (does not mutate the input tree roots).
 * @param {Array} menuTree same shape as MENU_TREE
 * @param {Array<{ screenId?: string, labelUser?: string }>} items
 */
export function mergeMenuLabels(menuTree, items) {
  const byScreen = labelItemsToUserLabelMap(items);
  if (byScreen.size === 0) return menuTree;

  return menuTree.map((node) => {
    const children = node.children
      ? node.children.map((child) => {
          const view = child.view;
          const id = child.id;
          const key =
            view && byScreen.has(view)
              ? view
              : id && view && id === view && byScreen.has(id)
                ? id
                : null;
          const override = key != null ? byScreen.get(key) : undefined;
          if (override !== undefined) {
            return { ...child, label: override };
          }
          return { ...child };
        })
      : node.children;
    return { ...node, children };
  });
}

/**
 * Merge label text + optional parent group + sort order into a presentation tree (req 20260407-screen-menu-parent-order).
 * Starts from MENU_TREE defaults; re-parents leaves and sorts siblings; tie-break by screenId asc.
 * @param {Array} menuTree same shape as MENU_TREE
 * @param {Array<{ screenId?: string, labelUser?: string, parentGroupId?: string|null, sortOrder?: number }>} items
 * @returns {typeof menuTree}
 */
export function buildMergedMenuTree(menuTree, items) {
  if (!Array.isArray(items) || items.length === 0) {
    return menuTree;
  }

  const itemsByScreen = new Map();
  items.forEach((it) => {
    if (it && typeof it.screenId === 'string' && it.screenId !== '') {
      itemsByScreen.set(it.screenId.trim(), it);
    }
  });

  /** @type {Map<string, { child: object, parentGroupId: string, sortOrder: number }>} */
  const leafDefaults = new Map();
  menuTree.forEach((node) => {
    (node.children || []).forEach((child, idx) => {
      const sid = child.view;
      if (!sid) return;
      leafDefaults.set(sid, {
        child: { ...child },
        parentGroupId: node.id,
        sortOrder: idx,
      });
    });
  });

  /** @type {Map<string, { child: object, parentGroupId: string, sortOrder: number, screenId: string }>} */
  const effective = new Map();
  leafDefaults.forEach((def, screenId) => {
    const it = itemsByScreen.get(screenId);
    let parent = def.parentGroupId;
    let order = def.sortOrder;
    let label = def.child.label;

    if (it) {
      if (typeof it.labelUser === 'string' && it.labelUser.trim() !== '') {
        label = it.labelUser.trim();
      }
      const pg = it.parentGroupId;
      if (typeof pg === 'string' && pg !== '' && VALID_PARENT_GROUP.has(pg)) {
        parent = pg;
      }
      if (typeof it.sortOrder === 'number' && Number.isFinite(it.sortOrder) && it.sortOrder >= 0) {
        order = it.sortOrder;
      }
    }

    effective.set(screenId, {
      screenId,
      parentGroupId: parent,
      sortOrder: order,
      child: { ...def.child, label },
    });
  });

  /** @type {Map<string, Array<{ screenId: string, sortOrder: number, child: object }>>} */
  const byParent = new Map();
  menuTree.forEach((n) => byParent.set(n.id, []));
  effective.forEach((meta) => {
    const bucket = byParent.get(meta.parentGroupId);
    if (!bucket) return;
    bucket.push({
      screenId: meta.screenId,
      sortOrder: meta.sortOrder,
      child: meta.child,
    });
  });

  return menuTree.map((node) => {
    const bucket = byParent.get(node.id) || [];
    const sorted = [...bucket].sort((a, b) => {
      if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
      return a.screenId.localeCompare(b.screenId);
    });
    return {
      ...node,
      children: sorted.map((b) => ({ ...b.child })),
    };
  });
}

const LOG_VIEWS = ['pb-feplog', 'pb-fep-log-search', 'java-fw-imagelog'];

/**
 * Apply API labelUser to log search heading names (LogGrid logType.name).
 * @param {Record<string, { id?: string, name?: string, description?: string }>} logTypeByViewDefault
 * @param {Array<{ screenId?: string, labelUser?: string }>} items
 */
export function applyLogTypeLabelOverrides(logTypeByViewDefault, items) {
  const byScreen = labelItemsToUserLabelMap(items);
  const out = { ...logTypeByViewDefault };
  LOG_VIEWS.forEach((view) => {
    const base = out[view];
    if (!base) return;
    const name = byScreen.get(view);
    if (name) {
      out[view] = { ...base, name };
    }
  });
  return out;
}
