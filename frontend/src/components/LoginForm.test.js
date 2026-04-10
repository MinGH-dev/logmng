import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm';

jest.mock('../utils/logger', () => ({
  info: jest.fn(),
  error: jest.fn(),
  debug: jest.fn(),
}));

/** Auth config 404 → local mode (matches authConfigService fallback). */
function mockFetchConfig404() {
  global.fetch = jest.fn((url) => {
    if (String(url).includes('/auth/config')) {
      return Promise.resolve({ ok: false, status: 404 });
    }
    return Promise.reject(new Error(`unexpected fetch ${url}`));
  });
}

describe('LoginForm', () => {
  const onLogin = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    mockFetchConfig404();
  });

  describe('TC-04: Login form label, placeholder, validation refer to employee number', () => {
    test('first field label is "사용자 ID (사번)"', async () => {
      render(<LoginForm onLogin={onLogin} />);
      await waitFor(() => {
        expect(screen.getByLabelText(/사용자 ID \(사번\)/)).toBeInTheDocument();
      });
    });

    test('employeeNumber input has placeholder and name employeeNumber', async () => {
      render(<LoginForm onLogin={onLogin} />);
      const input = await screen.findByPlaceholderText('사번을 입력하세요 (예: EMP-2026-0001)');
      expect(input).toBeInTheDocument();
      expect(input).toHaveAttribute('name', 'employeeNumber');
      expect(input).toHaveAttribute('type', 'text');
    });

    test('validation error refers to employee number when empty', async () => {
      render(<LoginForm onLogin={onLogin} />);
      const submit = await screen.findByRole('button', { name: '로그인' });
      await userEvent.click(submit);
      await screen.findByText('사용자 ID(사번)를 입력해주세요.');
      expect(screen.getByText('사용자 ID(사번)를 입력해주세요.')).toBeInTheDocument();
    });
  });

  describe('Login request body', () => {
    test('submit sends body with employeeNumber (string) and password', async () => {
      let capturedBody;
      global.fetch = jest.fn((url, options) => {
        if (String(url).includes('/auth/config')) {
          return Promise.resolve({ ok: false, status: 404 });
        }
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
      await userEvent.type(await screen.findByLabelText(/사용자 ID \(사번\)/), 'EMP-2026-0001');
      await userEvent.type(screen.getByPlaceholderText(/비밀번호/), 'mypass');
      await userEvent.click(screen.getByRole('button', { name: '로그인' }));

      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/auth/login'),
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        })
      );
      expect(capturedBody).toEqual({ employeeNumber: 'EMP-2026-0001', password: 'mypass' });
      expect(capturedBody).not.toHaveProperty('userId');
    });

    test('AD mode sends principal and password only', async () => {
      let capturedBody;
      global.fetch = jest.fn((url, options) => {
        if (String(url).includes('/auth/config')) {
          return Promise.resolve({
            ok: true,
            json: () => Promise.resolve({ success: true, data: { loginMode: 'ad' } }),
          });
        }
        capturedBody = options?.body ? JSON.parse(options.body) : null;
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({
            success: true,
            data: { user: { userId: 1, username: 'aduser' } },
          }),
        });
      });

      render(<LoginForm onLogin={onLogin} />);
      await screen.findByLabelText(/로그인 ID \(Principal\)/);
      await userEvent.type(screen.getByLabelText(/로그인 ID \(Principal\)/), 'user@corp.local');
      await userEvent.type(screen.getByPlaceholderText(/비밀번호/), 'secret');
      await userEvent.click(screen.getByRole('button', { name: '로그인' }));

      expect(capturedBody).toEqual({ principal: 'user@corp.local', password: 'secret' });
      expect(capturedBody).not.toHaveProperty('employeeNumber');
    });
  });

  describe('login API error messages', () => {
    async function submitLocalLogin() {
      render(<LoginForm onLogin={onLogin} />);
      await userEvent.type(await screen.findByLabelText(/사용자 ID \(사번\)/), 'EMP-2026-0001');
      await userEvent.type(screen.getByLabelText(/^비밀번호/), 'secret');
      await userEvent.click(screen.getByRole('button', { name: '로그인' }));
    }

    test('401 USER_ACCOUNT_DISABLED shows disabled-account message, not generic credentials', async () => {
      global.fetch = jest.fn((url, _options) => {
        if (String(url).includes('/auth/config')) {
          return Promise.resolve({ ok: false, status: 404 });
        }
        return Promise.resolve({
          ok: false,
          status: 401,
          json: () =>
            Promise.resolve({
              success: false,
              code: 'USER_ACCOUNT_DISABLED',
              error: 'Account disabled',
            }),
        });
      });

      await submitLocalLogin();
      expect(await screen.findByText(/비활성/)).toBeInTheDocument();
      expect(screen.queryByText(/사용자명과 비밀번호를 다시/)).not.toBeInTheDocument();
    });

    test('400 INVALID_INPUT shows server error string', async () => {
      global.fetch = jest.fn((url) => {
        if (String(url).includes('/auth/config')) {
          return Promise.resolve({ ok: false, status: 404 });
        }
        return Promise.resolve({
          ok: false,
          status: 400,
          json: () =>
            Promise.resolve({
              success: false,
              code: 'INVALID_INPUT',
              error: 'userId는 필수입니다.',
            }),
        });
      });

      await submitLocalLogin();
      expect(await screen.findByText('userId는 필수입니다.')).toBeInTheDocument();
    });
  });
});
