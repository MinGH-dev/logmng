/**
 * 2-depth checkbox tree for selecting allowed screens per permission group.
 * Uses MENU_TREE labels; a11y: role="checkbox", aria-checked, role="group".
 * For activity-log, statistics, search-history: scope dropdown ("본인만" | "전체"), default "본인만".
 * When screen is selected, shows checkboxes for read (label only), write, approve where applicable.
 * req 20250303-screen-function-checkbox-selection
 * onChange receives [{ screenId, scope?, read?, write?, approve? }].
 */
import React from 'react';
import { Tooltip } from '@mui/material';
import { MENU_TREE } from '../../constants/menuTree';
import {
  FUNCTION_LABELS,
  APPROVE_CHECKBOX_TOOLTIP,
  SCREENS_WITH_WRITE,
  SCREENS_WITH_APPROVE,
} from '../../constants/screenFunctionDescriptions';
import './ScreenSelectionTree.css';

/** Screens that support scope (self | all). req 20250303-activity-statistics-self-only-scope */
const SCOPE_SUPPORTING_SCREENS = ['activity-log', 'statistics', 'search-history'];

const SCOPE_OPTIONS = [
  { value: 'self', label: '본인만' },
  { value: 'all', label: '전체' },
];

/** Normalize selectedScreens to [{ screenId, scope?, read?, write?, approve? }] */
const normalizeSelected = (selected) => {
  if (!Array.isArray(selected)) return [];
  return selected.map((s) => {
    const base = typeof s === 'string'
      ? { screenId: s, scope: SCOPE_SUPPORTING_SCREENS.includes(s) ? 'self' : undefined }
      : { screenId: s.screenId, scope: s.scope || (SCOPE_SUPPORTING_SCREENS.includes(s.screenId) ? 'self' : undefined) };
    const hasWrite = SCREENS_WITH_WRITE.includes(base.screenId);
    const hasApprove = SCREENS_WITH_APPROVE.includes(base.screenId);
    return {
      ...base,
      read: base.read ?? true,
      write: base.write ?? (hasWrite ? true : undefined),
      approve: base.approve ?? (hasApprove ? false : undefined),
    };
  });
};

/** Get screenId set from normalized array */
const getScreenIdSet = (normalized) => new Set(normalized.map((s) => s.screenId));

/** Get item for screenId from normalized array */
const getItemForScreen = (normalized, screenId) =>
  normalized.find((s) => s.screenId === screenId);

const ScreenSelectionTree = ({ selectedScreens, onChange }) => {
  const normalized = React.useMemo(() => normalizeSelected(selectedScreens), [selectedScreens]);
  const screenIdSet = React.useMemo(() => getScreenIdSet(normalized), [normalized]);

  const toggleScreen = (view) => {
    if (!view) return;
    const next = [...normalized];
    const idx = next.findIndex((s) => s.screenId === view);
    const hasWrite = SCREENS_WITH_WRITE.includes(view);
    const hasApprove = SCREENS_WITH_APPROVE.includes(view);
    if (idx >= 0) {
      next.splice(idx, 1);
    } else {
      const scope = SCOPE_SUPPORTING_SCREENS.includes(view) ? 'self' : undefined;
      next.push({
        screenId: view,
        scope,
        read: true,
        write: hasWrite ? true : undefined,
        approve: hasApprove ? false : undefined,
      });
    }
    onChange(next);
  };

  const changeScope = (view, scope) => {
    const next = normalized.map((s) =>
      s.screenId === view ? { ...s, scope } : s
    );
    onChange(next);
  };

  const changeWrite = (view, checked) => {
    const next = normalized.map((s) =>
      s.screenId === view ? { ...s, write: checked } : s
    );
    onChange(next);
  };

  const changeApprove = (view, checked) => {
    const next = normalized.map((s) =>
      s.screenId === view ? { ...s, approve: checked } : s
    );
    onChange(next);
  };

  const isChecked = (view) => screenIdSet.has(view);
  const supportsScope = (view) => SCOPE_SUPPORTING_SCREENS.includes(view);
  const supportsWrite = (view) => SCREENS_WITH_WRITE.includes(view);
  const supportsApprove = (view) => SCREENS_WITH_APPROVE.includes(view);

  return (
    <div className="screen-selection-tree" role="group" aria-label="접근 화면 선택">
      {MENU_TREE.map((node) => (
        <div key={node.id} className="screen-selection-group" role="group" aria-label={node.label}>
          <div className="screen-selection-group-header">{node.label}</div>
          <ul className="screen-selection-list">
            {node.children.map((child) => {
              const view = child.view;
              const checked = isChecked(view);
              const item = getItemForScreen(normalized, view);
              const showScope = supportsScope(view) && checked;
              const scopeValue = item?.scope || 'self';
              const showWrite = supportsWrite(view) && checked;
              const showApprove = supportsApprove(view) && checked;
              const writeChecked = item?.write ?? true;
              const approveChecked = item?.approve ?? false;
              const approveTooltipId = `approve-tooltip-${child.id}`;

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
                  {checked && (
                    <span className="screen-selection-functions" role="group" aria-label={`${child.label} 권한`}>
                      {/* read: always true when selected; show as label or omit. main: read only. */}
                      <span className="screen-selection-read-label" aria-hidden="true">
                        {FUNCTION_LABELS.read}
                      </span>
                      {showWrite && (
                        <label className="screen-selection-fn-checkbox">
                          <input
                            type="checkbox"
                            checked={writeChecked}
                            onChange={(e) => changeWrite(view, e.target.checked)}
                            aria-checked={writeChecked}
                            aria-label={`${child.label} ${FUNCTION_LABELS.write}`}
                          />
                          <span>{FUNCTION_LABELS.write}</span>
                        </label>
                      )}
                      {showApprove && (
                        <label className="screen-selection-fn-checkbox">
                          <input
                            type="checkbox"
                            checked={approveChecked}
                            onChange={(e) => changeApprove(view, e.target.checked)}
                            aria-checked={approveChecked}
                            aria-label={`${child.label} ${FUNCTION_LABELS.approve}`}
                            aria-describedby={approveTooltipId}
                          />
                          <span id={approveTooltipId} className="screen-selection-sr-only">
                            {APPROVE_CHECKBOX_TOOLTIP}
                          </span>
                          <Tooltip title={APPROVE_CHECKBOX_TOOLTIP} arrow placement="right">
                            <span>{FUNCTION_LABELS.approve}</span>
                          </Tooltip>
                        </label>
                      )}
                    </span>
                  )}
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
