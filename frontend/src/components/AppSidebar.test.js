import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { appTheme } from '../theme';
import AppSidebar from './AppSidebar';
import logger from '../utils/logger';

jest.mock('../utils/logger', () => ({
  __esModule: true,
  default: {
    debug: jest.fn(),
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
  },
}));

const menuTree = [
  {
    id: 'group-log',
    label: '로그 검색',
    icon: () => <span data-testid="icon-group-log" />,
    children: [
      { id: 'pb-feplog', label: 'PB FEP v1.0.0', view: 'pb-feplog' },
    ],
  },
];

describe('AppSidebar collapsed submenu layering behavior', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
    document.body.innerHTML = '<main style="position: relative; z-index: 0;"></main>';
  });

  test('collapsed mode submenu remains interactable and emits diagnostic debug log', async () => {
    const onNavigate = jest.fn();
    render(
      <ThemeProvider theme={appTheme}>
        <AppSidebar
          open={false}
          isAdmin={false}
          allowedScreenIds={['pb-feplog']}
          currentView="pb-feplog"
          onNavigate={onNavigate}
          menuTree={menuTree}
        />
      </ThemeProvider>
    );

    const childMenu = await screen.findByText('PB FEP v1.0.0');
    await userEvent.click(childMenu);

    expect(onNavigate).toHaveBeenCalledWith('pb-feplog');
    await waitFor(() => {
      expect(logger.debug).toHaveBeenCalledWith(
        'Sidebar collapsed submenu layer diagnostic',
        expect.objectContaining({
          collapsed: true,
          hasOpenSubmenu: true,
        })
      );
    });
  });

  test('collapsed mode removes child submenu left indent padding', async () => {
    render(
      <ThemeProvider theme={appTheme}>
        <AppSidebar
          open={false}
          isAdmin={false}
          allowedScreenIds={['pb-feplog']}
          currentView="pb-feplog"
          onNavigate={jest.fn()}
          menuTree={menuTree}
        />
      </ThemeProvider>
    );

    const childMenu = await screen.findByText('PB FEP v1.0.0');
    const childButton = childMenu.closest('.ps-menu-button');
    expect(childButton).toBeInTheDocument();
    expect(childButton).toHaveStyle({ paddingLeft: '0px' });
  });

  test('expanded mode keeps child submenu indent padding', async () => {
    render(
      <ThemeProvider theme={appTheme}>
        <AppSidebar
          open
          isAdmin={false}
          allowedScreenIds={['pb-feplog']}
          currentView="pb-feplog"
          onNavigate={jest.fn()}
          menuTree={menuTree}
        />
      </ThemeProvider>
    );

    const childMenu = await screen.findByText('PB FEP v1.0.0');
    const childButton = childMenu.closest('.ps-menu-button');
    expect(childButton).toBeInTheDocument();
    expect(childButton).toHaveStyle({ paddingLeft: '48px' });
  });
});
