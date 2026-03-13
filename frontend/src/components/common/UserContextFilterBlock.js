import React from 'react';
import './UserContextFilterBlock.css';

/**
 * Reusable user-context filter block: 부서 → 사용자명 → 사용자 ID.
 * Unified order and labels per docs/requirements/20260310-search-ui-unify.md.
 *
 * @param {string} blockLabel - Group label for screen readers (e.g. "사용자", "요청자")
 * @param {boolean} hideUserFilters - When true, render nothing (scope=self)
 * @param {string[]} departmentList - Options for department select
 * @param {Array<{userId: string, ...}>} [userList] - If provided, userId is a select; otherwise text input
 * @param {{ department: string, username: string, userId: string }} values
 * @param {(name: 'department'|'username'|'userId', value: string) => void} onChange
 * @param {string} [idPrefix='user-ctx'] - Prefix for input ids (for a11y in same page)
 * @param {boolean} [compact] - When true, reduce margin for single-row inline layout (1–2 row UX)
 * @param {number} [usernameMaxLength=5] - Max length for 사용자명 (한글 기준); req 20260313
 */
const UserContextFilterBlock = ({
  blockLabel = '사용자',
  hideUserFilters = false,
  departmentList = [],
  userList,
  values = {},
  onChange,
  idPrefix = 'user-ctx',
  compact = false,
  usernameMaxLength = 5,
}) => {
  if (hideUserFilters) return null;

  const id = (name) => `${idPrefix}-${name}`;

  return (
    <fieldset className={`user-context-filter-block${compact ? ' user-context-filter-block--compact' : ''}`} aria-labelledby={id('legend')}>
      <legend id={id('legend')} className="user-context-filter-block__legend">
        {blockLabel}
      </legend>
      <div className="user-context-filter-block__row">
        <div className="form-group">
          <label htmlFor={id('department')}>부서</label>
          <select
            id={id('department')}
            value={values.department || ''}
            onChange={(e) => onChange('department', e.target.value)}
            className="form-control"
            aria-label="부서"
          >
            <option value="">전체</option>
            {(departmentList || []).map((dept) => (
              <option key={dept} value={dept}>{dept}</option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label htmlFor={id('username')}>사용자명 (최대 5자)</label>
          <input
            type="text"
            id={id('username')}
            value={values.username || ''}
            onChange={(e) => onChange('username', e.target.value)}
            className="form-control"
            placeholder="최대 5자"
            aria-label="사용자명 (최대 5자)"
            maxLength={usernameMaxLength}
          />
        </div>

        <div className="form-group">
          <label htmlFor={id('userId')}>{Array.isArray(userList) ? '사용자 ID' : '사용자 ID (8자리)'}</label>
          {Array.isArray(userList) ? (
            <select
              id={id('userId')}
              value={values.userId || ''}
              onChange={(e) => onChange('userId', e.target.value)}
              className="form-control"
              aria-label="사용자 ID"
            >
              <option value="">전체</option>
              {(userList || []).map((user) => (
                <option key={user.userId} value={user.userId}>
                  {user.userId}
                </option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              id={id('userId')}
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={8}
              value={values.userId || ''}
              onChange={(e) => {
                const v = e.target.value.replace(/\D/g, '').slice(0, 8);
                onChange('userId', v);
              }}
              className="form-control"
              placeholder="8자리 숫자"
              aria-label="사용자 ID (숫자 8자리)"
            />
          )}
        </div>
      </div>
    </fieldset>
  );
};

export default UserContextFilterBlock;
