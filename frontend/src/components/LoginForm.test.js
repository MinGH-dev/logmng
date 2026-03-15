import React from 'react';
import { render, screen } from '@testing-library/react';
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

    test('username input has placeholder for user ID', () => {
      render(<LoginForm onLogin={onLogin} />);
      const input = screen.getByPlaceholderText('사용자 ID를 입력하세요');
      expect(input).toBeInTheDocument();
      expect(input).toHaveAttribute('name', 'username');
    });

    test('validation error refers to user ID when username is empty', async () => {
      render(<LoginForm onLogin={onLogin} />);
      const submit = screen.getByRole('button', { name: '로그인' });
      await userEvent.click(submit);
      await screen.findByText('사용자 ID를 입력해주세요.');
      expect(screen.getByText('사용자 ID를 입력해주세요.')).toBeInTheDocument();
    });
  });
});
