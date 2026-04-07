import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider } from '@mui/material';
import { appTheme } from '../theme';
import NoPermissionDialog, { NO_PERMISSION_MESSAGE_KO } from './NoPermissionDialog';

const wrap = (ui) => <ThemeProvider theme={appTheme}>{ui}</ThemeProvider>;

describe('NoPermissionDialog (TC-F01)', () => {
  test('shows exact Korean message and primary confirm control', () => {
    const onConfirm = jest.fn();
    render(wrap(<NoPermissionDialog open onConfirm={onConfirm} />));

    expect(screen.getByText(NO_PERMISSION_MESSAGE_KO)).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: '확인' });
    expect(btn).toBeInTheDocument();
    fireEvent.click(btn);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});
