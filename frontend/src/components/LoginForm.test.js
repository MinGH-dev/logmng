import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm';

jest.mock('../utils/logger', () => ({
  info: jest.fn(),
  error: jest.fn(),
  debug: jest.fn(),
}));

describe('LoginForm', () => {
  const onLogin = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('TC-04: Login form label, placeholder, validation refer to user ID', () => {
    test('first field label is "사용자 ID"', () => {
      render(<LoginForm onLogin={onLogin} />);
      expect(screen.getByLabelText(/사용자 ID/)).toBeInTheDocument();
    });

    test('userId input has placeholder and name userId', () => {
      render(<LoginForm onLogin={onLogin} />);
      const input = screen.getByPlaceholderText('사용자 ID를 입력하세요 (예: 20260001)');
      expect(input).toBeInTheDocument();
      expect(input).toHaveAttribute('name', 'userId');
      expect(input).toHaveAttribute('type', 'number');
    });

    test('validation error refers to user ID when userId is empty', async () => {
      render(<LoginForm onLogin={onLogin} />);
      const submit = screen.getByRole('button', { name: '로그인' });
      await userEvent.click(submit);
      await screen.findByText('사용자 ID를 입력해주세요.');
      expect(screen.getByText('사용자 ID를 입력해주세요.')).toBeInTheDocument();
    });

    test('validation error when userId is not an integer', async () => {
      render(<LoginForm onLogin={onLogin} />);
      const userIdInput = screen.getByLabelText(/사용자 ID/);
      // Use decimal so validation rejects (userId must be integer); type="number" accepts "20.5" in jsdom
      fireEvent.change(userIdInput, { target: { name: 'userId', value: '20.5' } });
      const passwordInput = screen.getByLabelText(/비밀번호/);
      await userEvent.type(passwordInput, 'pass');
      await userEvent.click(screen.getByRole('button', { name: '로그인' }));
      expect(await screen.findByText(/사용자 ID는 숫자여야 합니다/)).toBeInTheDocument();
    });
  });

  describe('Login request body', () => {
    test('submit sends body with userId (number) and password, no username', async () => {
      let capturedBody;
      global.fetch = jest.fn((url, options) => {
        capturedBody = options?.body ? JSON.parse(options.body) : null;
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({
            success: true,
            data: { user: { userId: 20260001, username: 'testuser' } },
          }),
        });
      });

      render(<LoginForm onLogin={onLogin} />);
      await userEvent.type(screen.getByLabelText(/사용자 ID/), '20260001');
      await userEvent.type(screen.getByPlaceholderText(/비밀번호/), 'mypass');
      await userEvent.click(screen.getByRole('button', { name: '로그인' }));

      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/auth/login'),
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        })
      );
      expect(capturedBody).toEqual({ userId: 20260001, password: 'mypass' });
      expect(capturedBody).not.toHaveProperty('username');
    });
  });
});
