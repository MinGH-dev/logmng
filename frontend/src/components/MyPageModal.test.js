import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MyPageModal from './MyPageModal';

jest.mock('../services/authConfigService', () => ({
  fetchAuthLoginMode: jest.fn(),
}));

jest.mock('../services/myPageService', () => ({
  fetchAuthMe: jest.fn(),
  postOwnPassword: jest.fn(),
}));

const { fetchAuthLoginMode } = require('../services/authConfigService');
const { fetchAuthMe, postOwnPassword } = require('../services/myPageService');

describe('MyPageModal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('opens with read-only 부서/이름 and password section in local mode', async () => {
    fetchAuthLoginMode.mockResolvedValue('local');
    fetchAuthMe.mockResolvedValue({
      success: true,
      data: {
        user: {
          username: 'u1',
          selfContext: { department: '개발팀', username: '홍길동', userId: 20260001 },
        },
      },
    });

    render(<MyPageModal open onClose={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByLabelText(/부서/)).toBeDisabled();
    });
    expect(screen.getByLabelText(/부서/)).toHaveValue('개발팀');
    expect(screen.getByLabelText(/이름\(표시명\)/)).toBeDisabled();
    expect(screen.getByLabelText(/이름\(표시명\)/)).toHaveValue('홍길동');
    expect(screen.getByLabelText(/현재 비밀번호/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '비밀번호 변경' })).toBeInTheDocument();
  });

  test('AD mode hides password form and shows directory notice', async () => {
    fetchAuthLoginMode.mockResolvedValue('ad');
    fetchAuthMe.mockResolvedValue({
      success: true,
      data: {
        user: {
          selfContext: { department: '본사', username: 'AD User', userId: 1 },
        },
      },
    });

    render(<MyPageModal open onClose={jest.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/디렉터리\(AD\) 로그인/)).toBeInTheDocument();
    });
    expect(screen.queryByLabelText(/현재 비밀번호/)).not.toBeInTheDocument();
  });

  test('client validation: mismatch confirm shows error', async () => {
    fetchAuthLoginMode.mockResolvedValue('local');
    fetchAuthMe.mockResolvedValue({
      success: true,
      data: { user: { selfContext: { department: 'A', username: 'B', userId: 1 } } },
    });

    render(<MyPageModal open onClose={jest.fn()} />);
    await screen.findByLabelText(/현재 비밀번호/);

    await userEvent.type(screen.getByLabelText(/현재 비밀번호/), 'old');
    await userEvent.type(screen.getByLabelText(/^새 비밀번호$/), 'new1');
    await userEvent.type(screen.getByLabelText(/새 비밀번호 확인/), 'new2');
    await userEvent.click(screen.getByRole('button', { name: '비밀번호 변경' }));

    expect(await screen.findByText(/일치하지 않습니다/)).toBeInTheDocument();
    expect(postOwnPassword).not.toHaveBeenCalled();
  });

  test('successful submit calls POST with confirmNewPassword', async () => {
    fetchAuthLoginMode.mockResolvedValue('local');
    fetchAuthMe.mockResolvedValue({
      success: true,
      data: { user: { selfContext: { department: 'A', username: 'B', userId: 1 } } },
    });
    postOwnPassword.mockResolvedValue({ success: true });

    render(<MyPageModal open onClose={jest.fn()} />);
    await screen.findByLabelText(/현재 비밀번호/);

    await userEvent.type(screen.getByLabelText(/현재 비밀번호/), 'oldpass');
    await userEvent.type(screen.getByLabelText(/^새 비밀번호$/), 'newpass');
    await userEvent.type(screen.getByLabelText(/새 비밀번호 확인/), 'newpass');
    await userEvent.click(screen.getByRole('button', { name: '비밀번호 변경' }));

    await waitFor(() => {
      expect(postOwnPassword).toHaveBeenCalledWith({
        currentPassword: 'oldpass',
        newPassword: 'newpass',
        confirmNewPassword: 'newpass',
      });
    });
    expect(await screen.findByText(/비밀번호가 변경되었습니다/)).toBeInTheDocument();
  });
});
