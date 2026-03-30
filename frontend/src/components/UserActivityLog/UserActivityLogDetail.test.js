import React from 'react';
import { render, screen } from '@testing-library/react';
import UserActivityLogDetail from './UserActivityLogDetail';

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
});
