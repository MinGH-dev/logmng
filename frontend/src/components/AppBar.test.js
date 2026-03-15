import React from 'react';
import { render, screen } from '@testing-library/react';
import AppBar from './AppBar';

jest.mock('./AppSidebar', () => ({
  DRAWER_WIDTH_OPEN: 240,
  DRAWER_WIDTH_COLLAPSED: 64,
}));

describe('AppBar', () => {
  const defaultProps = {
    sidebarOpen: true,
    onToggleSidebar: jest.fn(),
    onLogout: jest.fn(),
  };

  describe('TC-05: Top bar shows [Team name] User name', () => {
    test('displays "[teamName] userName" when both provided', () => {
      render(
        <AppBar
          {...defaultProps}
          teamName="영업1팀"
          userName="홍길동"
        />
      );
      expect(screen.getByText('[영업1팀] 홍길동')).toBeInTheDocument();
    });

    test('displays only userName when teamName is empty', () => {
      render(
        <AppBar
          {...defaultProps}
          teamName=""
          userName="홍길동"
        />
      );
      expect(screen.getByText('홍길동')).toBeInTheDocument();
    });

    test('displays fallback when both empty', () => {
      render(
        <AppBar
          {...defaultProps}
          teamName=""
          userName=""
        />
      );
      expect(screen.getByText('사용자')).toBeInTheDocument();
    });

    test('displays [teamName] 사용자 when only teamName provided', () => {
      render(
        <AppBar
          {...defaultProps}
          teamName="영업1팀"
          userName=""
        />
      );
      expect(screen.getByText('[영업1팀] 사용자')).toBeInTheDocument();
    });
  });
});
