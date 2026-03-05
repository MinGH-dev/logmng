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

  // TC-16: approve=false → "조회만" radio checked
  test('TC-16: search-history with approve=false → "조회만" radio is checked', () => {
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
  test('TC-17: clicking "승인" radio → onChange with approve=true; clicking "조회만" → approve=false', () => {
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
