/**
 * 2-depth checkbox tree for selecting allowed screens per permission group.
 * Uses MENU_TREE labels; a11y: role="checkbox", aria-checked, role="group".
 */
import React from 'react';
import { MENU_TREE } from '../../constants/menuTree';
import './ScreenSelectionTree.css';

const ScreenSelectionTree = ({ selectedScreens, onChange }) => {
  const toggleScreen = (view) => {
    if (!view) return;
    const next = new Set(selectedScreens || []);
    if (next.has(view)) {
      next.delete(view);
    } else {
      next.add(view);
    }
    onChange(Array.from(next));
  };

  const isChecked = (view) => (selectedScreens || []).includes(view);

  return (
    <div className="screen-selection-tree" role="group" aria-label="접근 화면 선택">
      {MENU_TREE.map((node) => (
        <div key={node.id} className="screen-selection-group" role="group" aria-label={node.label}>
          <div className="screen-selection-group-header">{node.label}</div>
          <ul className="screen-selection-list">
            {node.children.map((child) => {
              const view = child.view;
              const checked = isChecked(view);
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
