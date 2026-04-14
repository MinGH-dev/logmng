/**
 * 2-depth checkbox tree for selecting allowed screens per permission group.
 * Uses MENU_TREE labels; a11y: role="checkbox", aria-checked, role="group".
 * For activity-log, statistics, search-history, pending-approvals: scope dropdown ("본인" | "부서" | "전체") for list/read only; approval scope is fixed to department (부서). Default "부서".
 * When screen is selected, shows checkboxes for read (label only), write, approve where applicable.
 * req 20250303-screen-function-checkbox-selection
 * onChange receives [{ screenId, scope?, read?, write?, approve? }].
 */
import React from 'react';
import { Tooltip } from '@mui/material';
import { MENU_TREE } from '../../constants/menuTree';
import { ADMIN_MATRIX_SIDEBAR_ALIAS_HINTS } from '../../constants/screenAccessPolicy';
import {
  FUNCTION_LABELS,
  APPROVE_CHECKBOX_TOOLTIP,
  SCREENS_WITH_WRITE,
  SCREENS_WITH_APPROVE,
  SCREENS_WITH_DECRYPT,
} from '../../constants/screenFunctionDescriptions';
import './ScreenSelectionTree.css';

/** Read label when screen is selected: read is always on. UX: clarify "조회 ✓" so it's clear read is always on. */
const READ_LABEL_DISPLAY = '조회 ✓';

/** Screens that support scope (self | team | all). req 20250304-team-scope-default-and-approval; req 20260305 pending-approvals scope; req 20260409 user-management-v2 */
const SCOPE_SUPPORTING_SCREENS = [
  'activity-log',
  'statistics',
  'search-history',
  'pending-approvals',
  'user-management-v2',
];

const SCOPE_OPTIONS = [
  { value: 'self', label: '본인' },
  { value: 'team', label: '부서' },
  { value: 'all', label: '전체' },
];

/** Default scope for scope-supporting screens when omitted. req 20250304-team-scope-default-and-approval */
const DEFAULT_SCOPE = 'team';

/** Screens where approval scope is fixed to department (team). req 20260306-approval-scope-fixed-department */
const APPROVAL_SCOPE_FIXED_SCREENS = [...SCREENS_WITH_APPROVE];

/** Normalize selectedScreens to [{ screenId, scope?, read?, write?, approve?, decrypt? }]. Undefined/null scope for scope-supporting screens → 'team'. When approve=true for approval-fixed screens, scope is forced to 'team'. decrypt only for main (req 20260306). */
const normalizeSelected = (selected) => {
  if (!Array.isArray(selected)) return [];
  return selected.map((s) => {
    const base = typeof s === 'string'
      ? { screenId: s, scope: SCOPE_SUPPORTING_SCREENS.includes(s) ? DEFAULT_SCOPE : undefined }
      : {
          screenId: s.screenId,
          scope: s.scope ?? (SCOPE_SUPPORTING_SCREENS.includes(s.screenId) ? DEFAULT_SCOPE : undefined),
          read: s.read,
          write: s.write,
          approve: s.approve,
          decrypt: s.decrypt,
        };
    const hasWrite = SCREENS_WITH_WRITE.includes(base.screenId);
    const hasApprove = SCREENS_WITH_APPROVE.includes(base.screenId);
    const hasDecrypt = SCREENS_WITH_DECRYPT.includes(base.screenId);
    const approved = base.approve ?? (hasApprove ? false : undefined);
    const decryptVal = base.decrypt ?? (hasDecrypt ? false : undefined);
    let scope = base.scope ?? (SCOPE_SUPPORTING_SCREENS.includes(base.screenId) ? DEFAULT_SCOPE : undefined);
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

/** Get screenId set from normalized array */
const getScreenIdSet = (normalized) => new Set(normalized.map((s) => s.screenId));

/** Get item for screenId from normalized array */
const getItemForScreen = (normalized, screenId) =>
  normalized.find((s) => s.screenId === screenId);

const ScreenSelectionTree = ({ selectedScreens, onChange, menuTree = MENU_TREE }) => {
  const normalized = React.useMemo(() => normalizeSelected(selectedScreens), [selectedScreens]);
  const screenIdSet = React.useMemo(() => getScreenIdSet(normalized), [normalized]);

  const toggleScreen = (view) => {
    if (!view) return;
    const next = [...normalized];
    const idx = next.findIndex((s) => s.screenId === view);
    const hasWrite = SCREENS_WITH_WRITE.includes(view);
    const hasApprove = SCREENS_WITH_APPROVE.includes(view);
    const hasDecrypt = SCREENS_WITH_DECRYPT.includes(view);
    if (idx >= 0) {
      next.splice(idx, 1);
    } else {
      const scope = SCOPE_SUPPORTING_SCREENS.includes(view) ? DEFAULT_SCOPE : undefined;
      next.push({
        screenId: view,
        scope,
        read: true,
        write: hasWrite ? true : undefined,
        approve: hasApprove ? false : undefined,
        decrypt: hasDecrypt ? false : undefined,
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
    const next = normalized.map((s) => {
      if (s.screenId !== view) return s;
      const updated = { ...s, approve: checked };
      if (checked === true && APPROVAL_SCOPE_FIXED_SCREENS.includes(view)) {
        updated.scope = 'team';
      }
      return updated;
    });
    onChange(next);
  };

  const changeDecrypt = (view, checked) => {
    const next = normalized.map((s) =>
      s.screenId === view ? { ...s, decrypt: checked } : s
    );
    onChange(next);
  };

  const isChecked = (view) => screenIdSet.has(view);
  const supportsScope = (view) => SCOPE_SUPPORTING_SCREENS.includes(view);
  const supportsWrite = (view) => SCREENS_WITH_WRITE.includes(view);
  const supportsApprove = (view) => SCREENS_WITH_APPROVE.includes(view);
  const supportsDecrypt = (view) => SCREENS_WITH_DECRYPT.includes(view);
  /** When true, scope is fixed to team (부서) and dropdown must be disabled. req 20260306 */
  const isApprovalScopeFixed = (view, approveChecked) =>
    supportsScope(view) && supportsApprove(view) && approveChecked === true;

  return (
    <div className="screen-selection-tree" role="group" aria-label="접근 화면 선택">
      {menuTree.map((node) => (
        <div key={node.id} className="screen-selection-group" role="group" aria-label={node.label}>
          <div className="screen-selection-group-header">{node.label}</div>
          <ul className="screen-selection-list">
            {node.children.map((child) => {
              const view = child.view;
              const checked = isChecked(view);
              const item = getItemForScreen(normalized, view);
              const showScope = supportsScope(view) && checked;
              const scopeFixed = isApprovalScopeFixed(view, item?.approve ?? false);
              const scopeValue = scopeFixed ? 'team' : (item?.scope ?? (supportsScope(view) ? DEFAULT_SCOPE : 'self'));
              const showWrite = supportsWrite(view) && checked;
              const showApprove = supportsApprove(view) && checked;
              const showDecrypt = supportsDecrypt(view) && checked;
              const writeChecked = item?.write ?? true;
              const approveChecked = item?.approve ?? false;
              const decryptChecked = item?.decrypt ?? false;
              const approveTooltipId = `approve-tooltip-${child.id}`;
              const sidebarAliasHint =
                node.id === 'admin' ? ADMIN_MATRIX_SIDEBAR_ALIAS_HINTS[view] : undefined;
              const aliasHintId = sidebarAliasHint ? `sidebar-alias-hint-${child.id}` : undefined;

              return (
                <li key={child.id} className="screen-selection-item">
                  <label className="screen-selection-label">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleScreen(view)}
                      aria-checked={checked}
                      aria-label={child.label}
                      aria-describedby={aliasHintId}
                    />
                    {sidebarAliasHint ? (
                      <Tooltip title={sidebarAliasHint} arrow placement="top">
                        <span id={aliasHintId}>{child.label}</span>
                      </Tooltip>
                    ) : (
                      <span>{child.label}</span>
                    )}
                  </label>
                  {checked && (
                    <span className="screen-selection-functions" role="group" aria-label={`${child.label} 권한`}>
                      {showApprove ? (
                        <span
                          className="screen-selection-approve-radio"
                          role="radiogroup"
                          aria-label={`${child.label} 권한 유형`}
                        >
                          <label
                            className={`screen-selection-radio-option${!approveChecked ? ' is-selected' : ''}`}
                          >
                            <input
                              type="radio"
                              name={`approve-${child.id}`}
                              value="view-only"
                              checked={!approveChecked}
                              onChange={() => changeApprove(view, false)}
                            />
                            <span>조회</span>
                          </label>
                          <Tooltip title={APPROVE_CHECKBOX_TOOLTIP} arrow placement="right">
                            <label
                              className={`screen-selection-radio-option${approveChecked ? ' is-selected' : ''}`}
                            >
                              <input
                                type="radio"
                                name={`approve-${child.id}`}
                                value="approve"
                                checked={approveChecked}
                                onChange={() => changeApprove(view, true)}
                                aria-describedby={approveTooltipId}
                              />
                              <span>{FUNCTION_LABELS.approve}</span>
                            </label>
                          </Tooltip>
                          <span id={approveTooltipId} className="screen-selection-sr-only" aria-hidden="true">
                            {APPROVE_CHECKBOX_TOOLTIP}
                          </span>
                        </span>
                      ) : (
                        <>
                          <span className="screen-selection-read-label" aria-hidden="true">
                            {READ_LABEL_DISPLAY}
                          </span>
                          {showWrite && (
                            <button
                              type="button"
                              className={`screen-selection-fn-toggle ${writeChecked ? 'is-on' : ''}`}
                              onClick={() => changeWrite(view, !writeChecked)}
                              aria-pressed={writeChecked}
                              aria-label={`${child.label} ${FUNCTION_LABELS.write}`}
                            >
                              {FUNCTION_LABELS.write}{writeChecked ? ' ✓' : ''}
                            </button>
                          )}
                          {showDecrypt && (
                            <button
                              type="button"
                              className={`screen-selection-fn-toggle ${decryptChecked ? 'is-on' : ''}`}
                              onClick={() => changeDecrypt(view, !decryptChecked)}
                              aria-pressed={decryptChecked}
                              aria-label={`${child.label} ${FUNCTION_LABELS.decrypt}`}
                            >
                              {FUNCTION_LABELS.decrypt}{decryptChecked ? ' ✓' : ''}
                            </button>
                          )}
                        </>
                      )}
                    </span>
                  )}
                  {showScope && (
                    scopeFixed ? (
                      <select
                        className="screen-selection-scope screen-selection-scope-readonly"
                        value="team"
                        disabled
                        aria-label={`${child.label} 조회 범위 (승인 시 부서 고정)`}
                        aria-readonly="true"
                      >
                        <option value="team">부서</option>
                      </select>
                    ) : (
                      <select
                        className="screen-selection-scope"
                        value={scopeValue}
                        onChange={(e) => changeScope(view, e.target.value)}
                        aria-label={`${child.label} 조회 범위`}
                      >
                        {SCOPE_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    )
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
