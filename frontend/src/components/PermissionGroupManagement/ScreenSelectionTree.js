/**
 * 2-depth checkbox tree for selecting allowed screens per permission group.
 * Uses MENU_TREE labels; a11y: role="checkbox", aria-checked, role="group".
 * For activity-log, statistics, search-history: scope dropdown ("본인만" | "전체"), default "본인만".
 * onChange receives [{ screenId, scope? }].
 */
import React from 'react';
import { MENU_TREE } from '../../constants/menuTree';
import './ScreenSelectionTree.css';

/** Screens that support scope (self | all). req 20250303-activity-statistics-self-only-scope */
const SCOPE_SUPPORTING_SCREENS = ['activity-log', 'statistics', 'search-history'];

const SCOPE_OPTIONS = [
  { value: 'self', label: '본인만' },
  { value: 'all', label: '전체' },
];

/** Normalize selectedScreens to [{ screenId, scope? }] */
const normalizeSelected = (selected) => {
  if (!Array.isArray(selected)) return [];
  return selected.map((s) =>
    typeof s === 'string' ? { screenId: s, scope: 'self' } : { screenId: s.screenId, scope: s.scope || 'self' }
  );
};

/** Get screenId set from normalized array */
const getScreenIdSet = (normalized) => new Set(normalized.map((s) => s.screenId));

/** Get scope for screenId from normalized array */
const getScopeForScreen = (normalized, screenId) => {
  const item = normalized.find((s) => s.screenId === screenId);
  return item?.scope || 'self';
};

const ScreenSelectionTree = ({ selectedScreens, onChange }) => {
  const normalized = React.useMemo(() => normalizeSelected(selectedScreens), [selectedScreens]);
  const screenIdSet = React.useMemo(() => getScreenIdSet(normalized), [normalized]);

  const toggleScreen = (view) => {
    if (!view) return;
    const next = [...normalized];
    const idx = next.findIndex((s) => s.screenId === view);
    if (idx >= 0) {
      next.splice(idx, 1);
    } else {
      const scope = SCOPE_SUPPORTING_SCREENS.includes(view) ? 'self' : undefined;
      next.push(scope !== undefined ? { screenId: view, scope } : { screenId: view });
    }
    onChange(next);
  };

  const changeScope = (view, scope) => {
    const next = normalized.map((s) =>
      s.screenId === view ? { ...s, scope } : s
    );
    onChange(next);
  };

  const isChecked = (view) => screenIdSet.has(view);
  const supportsScope = (view) => SCOPE_SUPPORTING_SCREENS.includes(view);

  return (
    <div className="screen-selection-tree" role="group" aria-label="접근 화면 선택">
      {MENU_TREE.map((node) => (
        <div key={node.id} className="screen-selection-group" role="group" aria-label={node.label}>
          <div className="screen-selection-group-header">{node.label}</div>
          <ul className="screen-selection-list">
            {node.children.map((child) => {
              const view = child.view;
              const checked = isChecked(view);
              const showScope = supportsScope(view) && checked;
              const scopeValue = getScopeForScreen(normalized, view);
              return (
                <li key={child.id} className="screen-selection-item">
                  <label className="screen-selection-label">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleScreen(view)}
                      aria-checked={checked}
                      aria-label={child.label}
                    />
                    <span>{child.label}</span>
                  </label>
                  {showScope && (
                    <select
                      className="screen-selection-scope"
                      value={scopeValue}
                      onChange={(e) => changeScope(view, e.target.value)}
                      aria-label={`${child.label} 데이터 범위`}
                    >
                      {SCOPE_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </div>
  );
};

export default ScreenSelectionTree;
