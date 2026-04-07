import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import UserActivityLogDetail from './UserActivityLogDetail';
import { postActivityLogPrivilegedReveal } from '../../services/userActivityLogService';

jest.mock('../../services/userActivityLogService', () => ({
  postActivityLogPrivilegedReveal: jest.fn(),
}));

const baseLog = (overrides = {}) => ({
  id: 1,
  user_id: 'u1',
  username: 'tester',
  action_type: 'PERMISSION_GROUP_UPDATE',
  ip_address: '127.0.0.1',
  user_agent: 'jest',
  request_method: 'PUT',
  request_path: '/api/permission-groups/1',
  response_status: 200,
  response_time_ms: 10,
  success: true,
  error_message: null,
  created_at: '2026-03-30T10:00:00',
  updated_at: '2026-03-30T10:00:00',
  ...overrides,
});

describe('UserActivityLogDetail', () => {
  beforeEach(() => {
    postActivityLogPrivilegedReveal.mockReset();
    postActivityLogPrivilegedReveal.mockResolvedValue({
      success: true,
      data: { copyBodyFull: 'FULL TEXT BODY', revealKind: 'COPY_BODY_FULL' },
    });
  });

  test('TC-06 UX: ASSIGN_USER_TO_PERMISSION_GROUP with before+after shows diff without “no prior group” note', () => {
    const log = baseLog({
      action_type: 'ASSIGN_USER_TO_PERMISSION_GROUP',
      action_detail: {
        permissionGroupAuditV1: {
          schemaVersion: '1',
          operation: 'ASSIGN_USER',
          permissionGroupId: 7,
          targetUserId: 'user-99',
          before: {
            code: 'OLD_GROUP',
            name: '이전 그룹',
            allowedScreens: [{ screenId: 'a', scope: 'team', read: true }],
          },
          after: {
            code: 'NEW_GROUP',
            name: '새 그룹',
            allowedScreens: [{ screenId: 'b', scope: 'all', read: true }],
          },
        },
      },
    });

    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);

    expect(screen.getByRole('heading', { name: '권한 그룹 감사' })).toBeInTheDocument();
    expect(screen.getByText('사용자 배정')).toBeInTheDocument();
    expect(screen.getByText('이전 그룹')).toBeInTheDocument();
    expect(screen.getByText('새 그룹')).toBeInTheDocument();
    expect(screen.queryByText(/이전 권한 그룹 없음/)).not.toBeInTheDocument();
    expect(screen.getByRole('table', { name: '권한 그룹 메타데이터 필드별 변경 전후' })).toBeInTheDocument();
  });

  test('TC-06 UX: ASSIGN_USER_TO_PERMISSION_GROUP with before null and after shows “이전 권한 그룹 없음” note', () => {
    const log = baseLog({
      action_type: 'ASSIGN_USER_TO_PERMISSION_GROUP',
      action_detail: {
        permissionGroupAuditV1: {
          schemaVersion: '1',
          operation: 'ASSIGN_USER',
          permissionGroupId: 7,
          targetUserId: 'user-99',
          before: null,
          after: {
            code: 'NEW_GROUP',
            name: '새 그룹',
            allowedScreens: [{ screenId: 'activity-log', scope: 'team', read: true }],
          },
        },
      },
    });

    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);

    expect(screen.getByText(/이전 권한 그룹 없음/)).toBeInTheDocument();
    expect(screen.getByText('새 그룹')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: '권한 그룹 메타데이터 필드별 변경 전후' })).toBeInTheDocument();
  });

  test('TC-11: PERMISSION_GROUP_UPDATE with permissionGroupAuditV1 shows structured audit section', () => {
    const log = baseLog({
      action_detail: {
        permissionGroupAuditV1: {
          schemaVersion: '1',
          operation: 'UPDATE',
          permissionGroupId: 42,
          permissionGroupCode: 'GENERAL_USER',
          changeReason: 'policy',
          before: {
            code: 'GENERAL_USER',
            name: '일반',
            description: 'old',
            sortOrder: 0,
            allowedScreens: [{ screenId: 'activity-log', scope: 'team', read: true }],
          },
          after: {
            code: 'GENERAL_USER',
            name: '일반 사용자',
            description: 'new',
            sortOrder: 0,
            allowedScreens: [{ screenId: 'activity-log', scope: 'all', read: true }],
          },
        },
      },
    });

    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);

    expect(screen.getByRole('heading', { name: '권한 그룹 감사' })).toBeInTheDocument();
    expect(screen.getByText('수정')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('policy')).toBeInTheDocument();
    expect(screen.getByText('일반')).toBeInTheDocument();
    expect(screen.getByText('일반 사용자')).toBeInTheDocument();
  });

  test('TC-12: unknown future keys render as JSON block; legacy enricher as rows', () => {
    const log = baseLog({
      action_detail: {
        permissionGroupAuditV1: {
          schemaVersion: '1',
          operation: 'UPDATE',
          permissionGroupId: 1,
        },
        permissionGroupCode: 'X',
        futureSchemaKey: { nested: true },
      },
    });

    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);

    expect(screen.getByRole('heading', { name: '추가 필드 (레거시 enricher)' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '기타 키 (JSON)' })).toBeInTheDocument();
    expect(screen.getByText(/futureSchemaKey/)).toBeInTheDocument();
  });

  test('요청 파라미터 (request_params): JSON 문자열 파싱 및 마스킹 표시', () => {
    const log = baseLog({
      action_type: 'VIEW',
      action_detail: {},
      request_params: JSON.stringify({ page: 1, password: 'secret-value' }),
    });
    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);
    expect(screen.getByRole('heading', { name: '요청 파라미터 (request_params)' })).toBeInTheDocument();
    const block = screen.getByRole('heading', { name: '요청 파라미터 (request_params)' }).parentElement;
    expect(block?.textContent).toMatch(/password/);
    expect(block?.textContent).toMatch(/\*\*\*/);
  });

  test('변경·삭제·추가 요약: 일반 UPDATE(before/after 객체) 시 필드별 비교 표', () => {
    const log = baseLog({
      action_type: 'CONFIG_PATCH',
      action_detail: {
        before: { alpha: 1, beta: 'old' },
        after: { alpha: 1, beta: 'new', gamma: true },
      },
    });
    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);
    expect(screen.getByRole('heading', { name: '변경·삭제·추가 요약' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '필드' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '변경 전' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '변경 후' })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /alpha/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /beta/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /gamma/ })).toBeInTheDocument();
  });

  test('TC-15: SEARCH action still shows search summary (regression)', () => {
    const log = baseLog({
      action_type: 'SEARCH',
      action_detail: {
        searchSummary: {
          totalCount: 100,
          resultCount: 20,
          currentPage: 1,
          totalPages: 5,
        },
      },
    });

    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);

    expect(screen.getByRole('heading', { name: '검색 결과 요약' })).toBeInTheDocument();
    expect(screen.getByText(/100/)).toBeInTheDocument();
  });

  test('IN_APP_COPY: shows Truncated badge and View full copy body when privilegedRevealCopyBodyAllowed', () => {
    const log = baseLog({
      action_type: 'IN_APP_COPY',
      privilegedRevealCopyBodyAllowed: true,
      action_detail: {
        copyPayload: {
          text: 'short…',
          was_truncated: true,
          length: 999,
        },
      },
    });
    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);
    expect(screen.getByText('Truncated')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /View full copy body/i })).toBeInTheDocument();
  });

  test('IN_APP_COPY: hides View full copy body when not privileged', () => {
    const log = baseLog({
      action_type: 'IN_APP_COPY',
      action_detail: {
        copyPayload: {
          text: 'short…',
          was_truncated: true,
          length: 999,
        },
      },
    });
    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);
    expect(screen.queryByRole('button', { name: /View full copy body/i })).not.toBeInTheDocument();
  });

  test('IN_APP_COPY: View full copy body replaces preview with API result', async () => {
    const log = baseLog({
      action_type: 'IN_APP_COPY',
      privilegedRevealCopyBodyAllowed: true,
      action_detail: {
        copyPayload: { text: 'trunc', was_truncated: true, length: 10 },
      },
    });
    render(<UserActivityLogDetail log={log} onClose={() => {}} actionTypeLabelMap={{}} />);
    fireEvent.click(screen.getByRole('button', { name: /View full copy body/i }));
    await waitFor(() => {
      expect(screen.getByText('FULL TEXT BODY')).toBeInTheDocument();
    });
    expect(postActivityLogPrivilegedReveal).toHaveBeenCalledWith(1, 'COPY_BODY_FULL');
  });
});
