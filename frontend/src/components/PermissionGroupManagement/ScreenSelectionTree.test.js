import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ScreenSelectionTree from './ScreenSelectionTree';

jest.mock('@mui/material', () => ({
  Tooltip: ({ children, title }) => (
    <span data-testid="tooltip" data-title={title}>{children}</span>
  ),
}));

describe('ScreenSelectionTree — approve radio group (Part 2)', () => {
  const noop = () => {};

  // TC-15: approve=true → "승인" radio checked
  test('TC-15: search-history with approve=true → "승인" radio is checked', () => {
    const selected = [{ screenId: 'search-history', approve: true }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    const radioGroup = screen.getByRole('radiogroup', { name: /검색 이력 권한 유형/ });
    const radios = within(radioGroup).getAllByRole('radio');
    expect(radios).toHaveLength(2);

    const viewOnlyRadio = radios.find(r => r.value === 'view-only');
    const approveRadio = radios.find(r => r.value === 'approve');
    expect(viewOnlyRadio).not.toBeChecked();
    expect(approveRadio).toBeChecked();

    expect(within(radioGroup).queryByText('조회 ✓')).toBeNull();
  });

  // TC-16: approve=false → "조회" radio checked
  test('TC-16: search-history with approve=false → "조회" radio is checked', () => {
    const selected = [{ screenId: 'search-history', approve: false }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    const radioGroup = screen.getByRole('radiogroup', { name: /검색 이력 권한 유형/ });
    const radios = within(radioGroup).getAllByRole('radio');

    const viewOnlyRadio = radios.find(r => r.value === 'view-only');
    const approveRadio = radios.find(r => r.value === 'approve');
    expect(viewOnlyRadio).toBeChecked();
    expect(approveRadio).not.toBeChecked();
  });

  // TC-17: clicking radios triggers onChange with correct approve value
  test('TC-17: clicking "승인" radio → onChange with approve=true; clicking "조회" → approve=false', () => {
    const handleChange = jest.fn();
    const selected = [{ screenId: 'search-history', approve: false }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={handleChange} />);

    const radioGroup = screen.getByRole('radiogroup', { name: /검색 이력 권한 유형/ });
    const radios = within(radioGroup).getAllByRole('radio');
    const approveRadio = radios.find(r => r.value === 'approve');
    const viewOnlyRadio = radios.find(r => r.value === 'view-only');

    userEvent.click(approveRadio);
    expect(handleChange).toHaveBeenCalledTimes(1);
    const callApprove = handleChange.mock.calls[0][0];
    const shItem = callApprove.find(s => s.screenId === 'search-history');
    expect(shItem.approve).toBe(true);

    handleChange.mockClear();
    const selected2 = [{ screenId: 'search-history', approve: true }];
    const { unmount } = render(
      <ScreenSelectionTree selectedScreens={selected2} onChange={handleChange} />
    );
    const radioGroup2 = screen.getAllByRole('radiogroup', { name: /검색 이력 권한 유형/ })[1];
    const viewOnly2 = within(radioGroup2).getAllByRole('radio').find(r => r.value === 'view-only');

    userEvent.click(viewOnly2);
    expect(handleChange).toHaveBeenCalledTimes(1);
    const callViewOnly = handleChange.mock.calls[0][0];
    const shItem2 = callViewOnly.find(s => s.screenId === 'search-history');
    expect(shItem2.approve).toBe(false);

    unmount();
  });

  // TC-18: write-only screen → write toggle renders, no radio group
  test('TC-18: user-management (write, no approve) → write toggle, no approve radio group', () => {
    const selected = [{ screenId: 'user-management' }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    expect(screen.queryByRole('radiogroup')).toBeNull();

    expect(screen.getByText('조회 ✓')).toBeInTheDocument();

    const writeBtn = screen.getByRole('button', { name: /사용자 관리 수정/ });
    expect(writeBtn).toBeInTheDocument();
  });
});

describe('ScreenSelectionTree — scope for pending-approvals (req 20260305)', () => {
  const noop = () => {};

  // TC-07: pending-approvals selected → scope dropdown visible; changing scope is sent on save
  test('TC-07: when "복호화 승인 관리" is selected, scope dropdown is visible and value is included in onChange', () => {
    const handleChange = jest.fn();
    const selected = [{ screenId: 'pending-approvals', scope: 'team', approve: false }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={handleChange} />);

    const scopeSelect = screen.getByRole('combobox', { name: /복호화 승인 관리 조회 범위/ });
    expect(scopeSelect).toBeInTheDocument();
    expect(scopeSelect).toHaveValue('team');
    expect(scopeSelect).not.toBeDisabled();

    const options = within(scopeSelect).getAllByRole('option');
    expect(options.map((o) => o.textContent)).toContain('본인');
    expect(options.map((o) => o.textContent)).toContain('부서');
    expect(options.map((o) => o.textContent)).toContain('전체');

    userEvent.selectOptions(scopeSelect, 'self');
    expect(handleChange).toHaveBeenCalledTimes(1);
    const payload = handleChange.mock.calls[0][0];
    const item = payload.find((s) => s.screenId === 'pending-approvals');
    expect(item).toBeDefined();
    expect(item.scope).toBe('self');
  });
});

describe('ScreenSelectionTree — user-management-v2 scope (req 20260409)', () => {
  test('TC-11: 사용자 관리 v2 선택 시 조회 범위 콤보박스가 보이고 값이 onChange에 포함된다', () => {
    const handleChange = jest.fn();
    const selected = [{ screenId: 'user-management-v2', scope: 'team', write: true }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={handleChange} />);

    const scopeSelect = screen.getByRole('combobox', { name: /사용자 관리 v2 조회 범위/ });
    expect(scopeSelect).toBeInTheDocument();
    expect(scopeSelect).toHaveValue('team');

    userEvent.selectOptions(scopeSelect, 'self');
    expect(handleChange).toHaveBeenCalled();
    const payload = handleChange.mock.calls[0][0];
    const item = payload.find((s) => s.screenId === 'user-management-v2');
    expect(item?.scope).toBe('self');
  });
});

describe('ScreenSelectionTree — approval scope fixed to department (req 20260306)', () => {
  const noop = () => {};

  // TC-01: search-history, "승인" → scope displayed as "부서", scope dropdown disabled
  test('TC-01: search-history with "승인" selected → scope is "부서" and dropdown is disabled', () => {
    const selected = [{ screenId: 'search-history', approve: true }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    const scopeSelect = screen.getByRole('combobox', { name: /검색 이력 조회 범위/ });
    expect(scopeSelect).toBeInTheDocument();
    expect(scopeSelect).toHaveValue('team');
    expect(scopeSelect).toBeDisabled();
    expect(within(scopeSelect).getByRole('option', { name: '부서' })).toBeInTheDocument();
  });

  // TC-02: pending-approvals, "승인" → "부서", dropdown disabled
  test('TC-02: pending-approvals with "승인" selected → scope is "부서" and dropdown is disabled', () => {
    const selected = [{ screenId: 'pending-approvals', approve: true }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    const scopeSelect = screen.getByRole('combobox', { name: /복호화 승인 관리 조회 범위/ });
    expect(scopeSelect).toBeInTheDocument();
    expect(scopeSelect).toHaveValue('team');
    expect(scopeSelect).toBeDisabled();
    expect(within(scopeSelect).getByRole('option', { name: '부서' })).toBeInTheDocument();
  });

  // TC-03: search-history, "조회" → scope dropdown enabled (본인/부서/전체)
  test('TC-03: search-history with "조회" selected → scope dropdown is enabled', () => {
    const selected = [{ screenId: 'search-history', approve: false, scope: 'team' }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    const scopeSelect = screen.getByRole('combobox', { name: /검색 이력 조회 범위/ });
    expect(scopeSelect).toBeInTheDocument();
    expect(scopeSelect).not.toBeDisabled();
    expect(scopeSelect).toHaveValue('team');
    expect(within(scopeSelect).getAllByRole('option').map((o) => o.textContent)).toEqual(
      expect.arrayContaining(['본인', '부서', '전체'])
    );
  });

  // TC-04: When saving with search-history approve=true, payload includes scope=team (via onChange)
  test('TC-04: selecting "승인" for search-history → onChange receives scope=team for that screen', () => {
    const handleChange = jest.fn();
    const selected = [{ screenId: 'search-history', approve: false, scope: 'self' }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={handleChange} />);

    const radioGroup = screen.getByRole('radiogroup', { name: /검색 이력 권한 유형/ });
    const approveRadio = within(radioGroup).getByRole('radio', { name: /승인/ });
    userEvent.click(approveRadio);

    expect(handleChange).toHaveBeenCalledTimes(1);
    const payload = handleChange.mock.calls[0][0];
    const item = payload.find((s) => s.screenId === 'search-history');
    expect(item).toBeDefined();
    expect(item.approve).toBe(true);
    expect(item.scope).toBe('team');
  });

  // TC-07 (integration): Load group with search-history approve=true → UI shows "승인" and "부서" read-only
  test('TC-07: load with search-history approve=true → "승인" selected and scope "부서" read-only/disabled', () => {
    const selected = [{ screenId: 'search-history', approve: true, scope: 'self' }];
    render(<ScreenSelectionTree selectedScreens={selected} onChange={noop} />);

    const radioGroup = screen.getByRole('radiogroup', { name: /검색 이력 권한 유형/ });
    const approveRadio = within(radioGroup).getByRole('radio', { name: /승인/ });
    expect(approveRadio).toBeChecked();

    const scopeSelect = screen.getByRole('combobox', { name: /검색 이력 조회 범위/ });
    expect(scopeSelect).toBeDisabled();
    expect(scopeSelect).toHaveValue('team');
  });

  // TC-08: Switch from "승인" to "조회" → scope dropdown becomes enabled
  test('TC-08: switch from "승인" to "조회" for search-history → scope dropdown becomes enabled', () => {
    const handleChange = jest.fn();
    const selected = [{ screenId: 'search-history', approve: true, scope: 'team' }];
    const { rerender } = render(<ScreenSelectionTree selectedScreens={selected} onChange={handleChange} />);

    const scopeSelectDisabled = screen.getByRole('combobox', { name: /검색 이력 조회 범위 \(승인 시 부서 고정\)/ });
    expect(scopeSelectDisabled).toBeDisabled();

    const radioGroup = screen.getByRole('radiogroup', { name: /검색 이력 권한 유형/ });
    const viewOnlyRadio = within(radioGroup).getByRole('radio', { name: /조회/ });
    userEvent.click(viewOnlyRadio);

    expect(handleChange).toHaveBeenCalledTimes(1);
    const payload = handleChange.mock.calls[0][0];
    const item = payload.find((s) => s.screenId === 'search-history');
    expect(item.approve).toBe(false);

    rerender(<ScreenSelectionTree selectedScreens={payload} onChange={noop} />);
    const scopeSelectEnabled = screen.getByRole('combobox', { name: /^검색 이력 조회 범위$/ });
    expect(scopeSelectEnabled).not.toBeDisabled();
  });
});
