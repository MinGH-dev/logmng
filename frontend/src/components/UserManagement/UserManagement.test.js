import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material';
import UserManagement from './UserManagement';
import {
  getUsers,
  deleteUser,
  createChildDepartmentV2,
  updateDepartmentV2,
  createDirectUserV2,
  deleteDepartmentV2,
  getQuickEntryOptionsV2,
} from '../../services/userService';
import { getUserPermissionHierarchy, listPermissionGroups } from '../../services/permissionGroupService';
import { appTheme } from '../../theme';

jest.mock('../../services/userService', () => ({
  getUsers: jest.fn(),
  deleteUser: jest.fn(),
  createChildDepartmentV2: jest.fn(),
  updateDepartmentV2: jest.fn(),
  createDirectUserV2: jest.fn(),
  deleteDepartmentV2: jest.fn(),
  getQuickEntryOptionsV2: jest.fn(),
}));
jest.mock('../../services/permissionGroupService', () => ({
  getUserPermissionHierarchy: jest.fn(),
  listPermissionGroups: jest.fn(),
}));
jest.mock('../../utils/logger', () => ({
  error: jest.fn(),
  warn: jest.fn(),
}));
jest.mock('../UserGroupAssignment/UserGroupAssignment', () => ({ userId, userGroups }) => (
  <div data-testid={`user-group-${userId}`} data-groups={JSON.stringify((userGroups || []).map((g) => g?.name || g?.id || g))} />
));
async function renderUserManagement(user) {
  const result = render(
    <ThemeProvider theme={appTheme}>
      <UserManagement user={user} />
    </ThemeProvider>
  );
  await waitFor(() => expect(getUserPermissionHierarchy).toHaveBeenCalled());
  await waitFor(() => expect(getQuickEntryOptionsV2).toHaveBeenCalled());
  return result;
}

describe('UserManagement', () => {
  const adminUser = {
    isSystemAdmin: true,
    allowedScreenIds: ['user-permission-hierarchy'],
    screenFunctions: {
      'user-permission-hierarchy': { write: true },
    },
  };

  beforeEach(() => {
    jest.clearAllMocks();
    getUsers.mockResolvedValue({ data: [{ userId: 20260001, isApprover: false }] });
    listPermissionGroups.mockResolvedValue([]);
    createChildDepartmentV2.mockResolvedValue({ success: true, data: { id: 502 } });
    updateDepartmentV2.mockResolvedValue({ success: true, data: { id: 502 } });
    deleteDepartmentV2.mockResolvedValue({ success: true, data: null });
    getQuickEntryOptionsV2.mockResolvedValue({
      data: {
        employeeNumber: { previous: '20261234', recent: ['20261234'] },
        name: { previous: '홍길동', recent: ['홍길동'] },
        rank: { previous: '대리', recent: ['대리', '과장'] },
        permissionGroupId: { previous: 3, recent: [3] },
      },
    });
  });

  describe('Table columns', () => {
    test('table includes "사용자명" column and shows employee number fallback text when missing', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '개발1팀',
            children: [],
            users: [
              { userId: 20260001, userName: '홍길동', rank: '대리', position: '개발', permissionGroups: [] },
              { userId: 20260002, userName: null, rank: '-', position: '-', permissionGroups: [] },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('개발1팀')).toBeInTheDocument();
      });

      const expandButton = screen.getByRole('button', { name: /^펼치기$/ });
      await userEvent.click(expandButton);

      await waitFor(() => {
        expect(screen.getByRole('columnheader', { name: '사용자명' })).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: '사용자 ID' })).toBeInTheDocument();
      });

      expect(screen.getAllByText('홍길동').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('사번 미등록').length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('사용자 ID column', () => {
    test('prefers employeeNumber over app userId when present', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '인사연동팀',
            children: [],
            users: [
              {
                userId: 20270001,
                employeeNumber: '20261001',
                userName: '김직원',
                rank: '-',
                position: '-',
                permissionGroups: [],
              },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('인사연동팀')).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /^펼치기$/ }));

      await waitFor(() => {
        expect(screen.getByText('김직원')).toBeInTheDocument();
        expect(screen.getByText('20261001')).toBeInTheDocument();
      });

      expect(screen.getByTestId('user-group-20270001')).toBeInTheDocument();
    });

    test('falls back to employee_number (snake_case) when employeeNumber absent', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D2',
            name: '스네이크팀',
            children: [],
            users: [
              {
                userId: 99,
                employee_number: 'EMP-SN-1',
                userName: '박직원',
                rank: '-',
                position: '-',
                permissionGroups: [],
              },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('스네이크팀')).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /^펼치기$/ }));

      await waitFor(() => {
        expect(screen.getByText('EMP-SN-1')).toBeInTheDocument();
      });
    });
  });

  describe('TC-09: 사용자 삭제 다이얼로그', () => {
    test('사유 없이 삭제할 수 없고, 사유 입력 후 성공 시 API 호출 및 목록 갱신', async () => {
      deleteUser.mockResolvedValue({ success: true, data: null });
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '팀',
            children: [],
            users: [
              {
                userId: 20260001,
                userName: '삭제대상',
                employeeNumber: 'E99',
                rank: '-',
                position: '-',
                permissionGroups: [],
              },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('팀')).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /^펼치기$/ }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /삭제대상 사용자 삭제/ })).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: /삭제대상 사용자 삭제/ }));

      await waitFor(() => {
        expect(screen.getByRole('dialog', { name: '사용자 삭제' })).toBeInTheDocument();
      });

      await userEvent.click(screen.getByRole('button', { name: '삭제' }));

      await waitFor(() => {
        expect(screen.getByText('삭제 사유를 입력하세요.')).toBeInTheDocument();
      });

      expect(deleteUser).not.toHaveBeenCalled();

      const reasonInput = screen.getByLabelText(/삭제 사유/);
      await userEvent.type(reasonInput, '  퇴사 처리  ');

      await userEvent.click(screen.getByRole('button', { name: '삭제' }));

      await waitFor(() => {
        expect(deleteUser).toHaveBeenCalledWith(20260001, { changeReason: '퇴사 처리' });
      });

      await waitFor(() => {
        expect(screen.queryByRole('dialog', { name: '사용자 삭제' })).not.toBeInTheDocument();
      });

      expect(getUserPermissionHierarchy.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });

  describe('v2 사용자 등록', () => {
    test('트리 액션으로 사용자 추가 모달을 열고 제출 시 v2 API 호출', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '팀', children: [], users: [] }],
      });
      listPermissionGroups.mockResolvedValue([{ id: 3, name: '운영자' }]);
      createDirectUserV2.mockResolvedValue({ success: true, data: { userId: 20261234 } });

      await renderUserManagement(adminUser);

      await waitFor(() => {
        expect(screen.getByText('팀')).toBeInTheDocument();
      });
      await userEvent.click(screen.getByRole('button', { name: '팀' }));
      await userEvent.click(screen.getByRole('button', { name: '사용자 추가' }));
      await waitFor(() => {
        expect(screen.getByRole('dialog', { name: '사용자 추가' })).toBeInTheDocument();
      });
      expect(screen.getByText(/대상 부서:/)).toHaveTextContent('대상 부서: 팀');
      expect(screen.queryByText(/\[D1\]/)).not.toBeInTheDocument();
      await userEvent.type(screen.getByLabelText('사번 *'), '20269999');
      await userEvent.type(screen.getByLabelText('이름 *'), '테스터');
      await userEvent.type(screen.getByLabelText('직급 *'), '대리');
      await userEvent.selectOptions(screen.getByLabelText('권한 그룹 *'), '3');
      await userEvent.type(screen.getByLabelText('등록 사유 *'), '신규 등록');
      await userEvent.click(screen.getByRole('button', { name: '사용자 등록' }));

      await waitFor(() => {
        expect(createDirectUserV2).toHaveBeenCalledWith({
          departmentId: 'D1',
          employeeNumber: '20269999',
          name: '테스터',
          rank: '대리',
          permissionGroupId: 3,
          changeReason: '신규 등록',
        });
      });
      await waitFor(() => {
        expect(getUserPermissionHierarchy).toHaveBeenCalledTimes(2);
      });
    });

    test('필수값 누락이면 사용자 등록 API 호출 없이 오류를 표시한다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '팀', children: [], users: [] }],
      });
      listPermissionGroups.mockResolvedValue([{ id: 3, name: '운영자' }]);

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '팀' }));
      await userEvent.click(screen.getByRole('button', { name: '사용자 추가' }));
      await waitFor(() => expect(screen.getByRole('dialog', { name: '사용자 추가' })).toBeInTheDocument());
      expect(screen.getByRole('button', { name: '사용자 등록' })).toBeDisabled();
      expect(createDirectUserV2).not.toHaveBeenCalled();
    });

    test('직전값 사용 버튼으로 필드 재사용 가능', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '팀', children: [], users: [] }],
      });
      listPermissionGroups.mockResolvedValue([{ id: 3, name: '운영자' }]);
      createDirectUserV2.mockResolvedValue({ success: true, data: { userId: 1 } });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '팀' }));
      await userEvent.click(screen.getByRole('button', { name: '사용자 추가' }));
      await waitFor(() => expect(screen.getByRole('dialog', { name: '사용자 추가' })).toBeInTheDocument());
      await userEvent.type(screen.getByLabelText('사번 *'), '20261111');
      await userEvent.type(screen.getByLabelText('이름 *'), '반복자');
      await userEvent.type(screen.getByLabelText('직급 *'), '과장');
      await userEvent.selectOptions(screen.getByLabelText('권한 그룹 *'), '3');
      await userEvent.type(screen.getByLabelText('등록 사유 *'), '1차 등록');
      await userEvent.click(screen.getByRole('button', { name: '사용자 등록' }));
      await waitFor(() => expect(createDirectUserV2).toHaveBeenCalledTimes(1));
      await waitFor(() => expect(getUserPermissionHierarchy).toHaveBeenCalledTimes(2));
      await waitFor(() => expect(screen.queryByRole('dialog', { name: '사용자 추가' })).not.toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: '팀' }));
      await userEvent.click(screen.getByRole('button', { name: '사용자 추가' }));
      await waitFor(() => expect(screen.getByRole('dialog', { name: '사용자 추가' })).toBeInTheDocument());
      const previousButtons = screen.getAllByRole('button', { name: '직전값 사용' });
      await userEvent.click(previousButtons[2]); // rank
      expect(screen.getByLabelText('직급 *')).toHaveValue('과장');
    });
  });

  describe('TC-02: 수동 부서 트리 편집', () => {
    test('트리 라벨은 부서명만 표시되고 코드 접두는 보이지 않는다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '기존팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '기존팀' })).toBeInTheDocument());
      expect(screen.queryByText('[D1] 기존팀')).not.toBeInTheDocument();
      expect(screen.getByText(/선택 부서:/)).toHaveTextContent('선택 부서: 미선택');
      await userEvent.click(screen.getByRole('button', { name: '기존팀' }));
      expect(screen.getByText(/선택 부서:/)).toHaveTextContent('선택 부서: 기존팀');
      expect(screen.queryByText(/\[D1\]/)).not.toBeInTheDocument();
    });

    test('선택 노드 액션이 아이콘 버튼으로 렌더링되고 title tooltip을 가진다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '기존팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '기존팀' })).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '기존팀' }));

      const addDept = screen.getByRole('button', { name: '하위 부서 추가' });
      const editDept = screen.getByRole('button', { name: '부서 수정' });
      const addUser = screen.getByRole('button', { name: '사용자 추가' });
      const deleteDept = screen.getByRole('button', { name: '부서 삭제' });

      expect(addDept).toHaveAttribute('title', '하위 부서 추가');
      expect(editDept).toHaveAttribute('title', '부서 수정');
      expect(addUser).toHaveAttribute('title', '사용자 추가');
      expect(deleteDept).toHaveAttribute('title', '부서 삭제');
    });

    test('미배치(__UNASSIGNED__) 노드는 부서 편집 액션(하위 추가 등)이 보이지 않는다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          { id: 1, code: 'REAL', name: '실부서', children: [], users: [] },
          { id: 2, code: '__UNASSIGNED__', name: '미배치', children: [], users: [] },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '미배치' })).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '미배치' }));

      expect(screen.queryByRole('button', { name: '하위 부서 추가' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '부서 수정' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '사용자 추가' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '부서 삭제' })).not.toBeInTheDocument();
      expect(createChildDepartmentV2).not.toHaveBeenCalled();
    });

    test('하위 부서 저장 시 createChildDepartmentV2에 트리에서 연 부모 코드가 그대로 전달된다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            id: 1,
            code: 'PARENT-CODE',
            name: '상위',
            children: [],
            users: [],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '상위' })).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '상위' }));
      await userEvent.click(screen.getByRole('button', { name: '하위 부서 추가' }));

      const childDialog = await screen.findByRole('dialog', { name: '하위 부서 추가' });
      await userEvent.type(within(childDialog).getByLabelText(/부서명/), '자식');
      await userEvent.type(within(childDialog).getByLabelText(/부서코드/), 'CHILD-1');
      await userEvent.type(within(childDialog).getByLabelText(/변경 사유/), '생성');
      await userEvent.click(within(childDialog).getByRole('button', { name: '저장' }));

      await waitFor(() => {
        expect(createChildDepartmentV2).toHaveBeenCalledWith(
          'PARENT-CODE',
          expect.objectContaining({
            name: '자식',
            code: 'CHILD-1',
            changeReason: '생성',
          })
        );
      });
    });

    test('하위 부서 추가 모달에서 저장 시 createChildDepartmentV2를 올바르게 호출한다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          { id: 101, code: 'D1', name: '기존팀', children: [], users: [] },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('기존팀')).toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: '기존팀' }));
      await userEvent.click(screen.getByRole('button', { name: '하위 부서 추가' }));
      const childDialog = await screen.findByRole('dialog', { name: '하위 부서 추가' });
      await userEvent.type(within(childDialog).getByLabelText(/부서명/), '하위팀A');
      await userEvent.type(within(childDialog).getByLabelText(/부서코드/), 'D1-A');
      await userEvent.type(within(childDialog).getByLabelText(/변경 사유/), '하위 생성');
      await userEvent.click(within(childDialog).getByRole('button', { name: '저장' }));

      await waitFor(() => {
        expect(createChildDepartmentV2).toHaveBeenCalled();
      });
      const [parentId, payload] = createChildDepartmentV2.mock.calls[0];
      expect(parentId).toBe('D1');
      expect(payload.name).toBe('하위팀A');
      expect(payload.changeReason).toBe('하위 생성');
      expect(payload.code).toBe('D1-A');
    });

    test('부서 생성 시 code 미입력은 검증 오류를 표시하고 API를 호출하지 않는다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '기존팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '기존팀' })).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '기존팀' }));
      await userEvent.click(screen.getByRole('button', { name: '하위 부서 추가' }));
      const childDialog = await screen.findByRole('dialog', { name: '하위 부서 추가' });
      await userEvent.type(within(childDialog).getByLabelText(/부서명/), '하위팀B');
      await userEvent.type(within(childDialog).getByLabelText(/변경 사유/), '조직 개편');
      await userEvent.click(within(childDialog).getByRole('button', { name: '저장' }));

      await waitFor(() => {
        expect(screen.getByText('부서코드를 입력하세요.')).toBeInTheDocument();
      });
      expect(createChildDepartmentV2).not.toHaveBeenCalled();
    });

    test('부서 수정 모달에서 저장 시 updateDepartmentV2 API를 호출한다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '기존팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '기존팀' })).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '기존팀' }));
      await userEvent.click(screen.getByRole('button', { name: '부서 수정' }));

      const editDialog = await screen.findByRole('dialog', { name: '부서 수정' });
      expect(within(editDialog).getByLabelText('부서코드')).toBeDisabled();
      await userEvent.clear(within(editDialog).getByLabelText(/부서명/));
      await userEvent.type(within(editDialog).getByLabelText(/부서명/), '수정팀');
      await userEvent.type(within(editDialog).getByLabelText(/변경 사유/), '이름 정정');
      await userEvent.click(within(editDialog).getByRole('button', { name: '저장' }));

      await waitFor(() => {
        expect(updateDepartmentV2).toHaveBeenCalledWith('D1', {
          name: '수정팀',
          changeReason: '이름 정정',
        });
      });
    });

    test('부서 삭제 모달에서 사유 검증 후 API를 호출한다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '기존팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('기존팀')).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '기존팀' }));
      await userEvent.click(screen.getByRole('button', { name: '부서 삭제' }));
      const deleteDialog = await screen.findByRole('dialog', { name: '부서 삭제' });
      expect(within(deleteDialog).getByText('기존팀')).toBeInTheDocument();
      expect(within(deleteDialog).queryByText(/\[D1\]/)).not.toBeInTheDocument();
      await userEvent.click(within(deleteDialog).getByRole('button', { name: '삭제' }));

      await waitFor(() => {
        expect(screen.getByText('변경 사유를 입력하세요.')).toBeInTheDocument();
      });
      expect(deleteDepartmentV2).not.toHaveBeenCalled();

      await userEvent.type(within(deleteDialog).getByLabelText(/변경 사유/), '  조직 변경 반영  ');
      await userEvent.click(within(deleteDialog).getByRole('button', { name: '삭제' }));

      await waitFor(() => {
        expect(deleteDepartmentV2).toHaveBeenCalledWith('D1', { changeReason: '조직 변경 반영' });
      });
    });
  });

  describe('TC-09: 최근값 선택', () => {
    test('최근값 선택 드롭다운으로 사번/권한 그룹을 즉시 반영한다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '팀', children: [], users: [] }],
      });
      listPermissionGroups.mockResolvedValue([{ id: 3, name: '운영자' }, { id: 7, name: '감사자' }]);
      getQuickEntryOptionsV2.mockResolvedValue({
        data: {
          employeeNumber: { previous: null, recent: ['20265555', '20264444'] },
          name: { previous: null, recent: [] },
          rank: { previous: null, recent: [] },
          permissionGroupId: { previous: null, recent: [7, 3] },
        },
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: '팀' }));
      await userEvent.click(screen.getByRole('button', { name: '사용자 추가' }));
      await waitFor(() => expect(screen.getByRole('dialog', { name: '사용자 추가' })).toBeInTheDocument());

      await userEvent.selectOptions(screen.getByLabelText('사번 최근값 선택'), '20265555');
      expect(screen.getByLabelText('사번 *')).toHaveValue('20265555');

      await userEvent.selectOptions(screen.getByLabelText('권한 최근값 선택'), '7');
      expect(screen.getByLabelText('권한 그룹 *')).toHaveValue('7');
    });
  });

  describe('req 20260409 UM v2 access & scope (TC-12, TC-13)', () => {
    test('TC-12: allowedScreenIds에 user-management-v2만 있어도 계층·퀵엔트리 API를 호출해 데이터를 로드한다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ code: 'D1', name: '팀', children: [], users: [] }],
      });
      const v2OnlyUser = {
        isSystemAdmin: false,
        allowedScreenIds: ['user-management-v2'],
        screenFunctions: { 'user-management-v2': { read: true, write: false } },
        screenScopes: { 'user-management-v2': 'team' },
      };

      await renderUserManagement(v2OnlyUser);

      await waitFor(() => {
        expect(getUserPermissionHierarchy).toHaveBeenCalled();
        expect(getUsers).toHaveBeenCalled();
        expect(listPermissionGroups).toHaveBeenCalled();
      });
      expect(screen.queryByText('관리자만 접근할 수 있습니다.')).not.toBeInTheDocument();
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());
    });

    test('TC-13: screenScopes[self]이면 본인 고정 블록이 selfContext 사번 값으로 표시된다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ code: 'D1', name: '팀', children: [], users: [] }],
      });
      const selfScopeUser = {
        isSystemAdmin: false,
        allowedScreenIds: ['user-management-v2'],
        screenFunctions: { 'user-management-v2': { read: true, write: false } },
        screenScopes: { 'user-management-v2': 'self' },
        selfContext: {
          department: '개발팀',
          username: 'selfuser',
          userId: 90001,
          employeeNumber: 'EMP-90001',
        },
      };

      await renderUserManagement(selfScopeUser);

      await waitFor(() => expect(screen.getByTestId('um-v2-locked-self-block')).toBeInTheDocument());
      expect(screen.getByLabelText('부서명 (본인 고정)')).toHaveValue('개발팀');
      expect(screen.getByLabelText('사용자명 (본인 고정)')).toHaveValue('selfuser');
      expect(screen.getByLabelText('사용자 ID (사번, 본인 고정)')).toHaveValue('EMP-90001');
      expect(screen.queryByRole('button', { name: '검색' })).not.toBeInTheDocument();
    });
  });

  describe('TC-11: read-only 권한', () => {
    test('쓰기 권한이 없으면 트리/사용자 변경 액션이 차단된다', async () => {
      const readOnlyUser = {
        isSystemAdmin: false,
        allowedScreenIds: ['user-permission-hierarchy'],
        screenFunctions: {
          'user-permission-hierarchy': { write: false },
        },
      };
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            id: 101,
            code: 'D1',
            name: '팀',
            children: [],
            users: [{ userId: 20260001, userName: '읽기전용', permissionGroups: [] }],
          },
        ],
      });

      await renderUserManagement(readOnlyUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());
      await userEvent.click(screen.getByRole('button', { name: /^펼치기$/ }));
      await userEvent.click(screen.getByRole('button', { name: '팀' }));

      expect(screen.queryByRole('button', { name: '인사정보 기반 등록(전환 기간)' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '하위 부서 추가' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '부서 수정' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '사용자 추가' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '부서 삭제' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /사용자 삭제/ })).not.toBeInTheDocument();
    });
  });

  describe('req 20260408 grid search: TC-03 department name filter', () => {
    test('applies 부서명 substring and expands matching branch', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'DI',
            name: '디지털본부',
            children: [
              {
                code: 'DI-PLAT',
                name: '플랫폼개발팀',
                children: [],
                users: [{ userId: 1, userName: '김개발', employeeNumber: 'E1', rank: '-', position: '-', permissionGroups: [] }],
              },
            ],
            users: [],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '디지털본부' })).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('부서명 필터'), '플랫');
      await userEvent.click(screen.getByRole('button', { name: '검색' }));

      await waitFor(() => {
        expect(screen.getByText('플랫폼개발팀')).toBeInTheDocument();
        expect(screen.getByText('김개발')).toBeInTheDocument();
      });
    });
  });

  describe('req 20260408 grid search: TC-04 user name filter', () => {
    test('hides non-matching user rows when filtering by 사용자명', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '동일부서',
            children: [],
            users: [
              { userId: 1, userName: '홍길동', employeeNumber: '100', rank: '-', position: '-', permissionGroups: [] },
              { userId: 2, userName: '김철수', employeeNumber: '200', rank: '-', position: '-', permissionGroups: [] },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('동일부서')).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('사용자명 필터'), '홍길');
      await userEvent.click(screen.getByRole('button', { name: '검색' }));

      await waitFor(() => {
        expect(screen.getByText('홍길동')).toBeInTheDocument();
      });
      expect(screen.queryByText('김철수')).not.toBeInTheDocument();
    });
  });

  describe('req 20260408 grid search: TC-05 employee number filter', () => {
    test('filters by 사번 substring', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '팀',
            children: [],
            users: [
              { userId: 1, userName: 'A', employeeNumber: '20269901', rank: '-', position: '-', permissionGroups: [] },
              { userId: 2, userName: 'B', employeeNumber: '20268888', rank: '-', position: '-', permissionGroups: [] },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('사번 필터'), '990');
      await userEvent.click(screen.getByRole('button', { name: '검색' }));

      await waitFor(() => expect(screen.getByText('20269901')).toBeInTheDocument());
      expect(screen.queryByText('20268888')).not.toBeInTheDocument();
    });
  });

  describe('req 20260408 grid search: TC-06 multiple branches', () => {
    test('expands ancestors so matches in separate branches are both visible', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'R',
            name: '루트',
            children: [
              {
                code: 'A',
                name: '브랜치A',
                children: [],
                users: [{ userId: 1, userName: '공통이름', employeeNumber: 'EA1', rank: '-', position: '-', permissionGroups: [] }],
              },
              {
                code: 'B',
                name: '브랜치B',
                children: [],
                users: [{ userId: 2, userName: '공통이름', employeeNumber: 'EB2', rank: '-', position: '-', permissionGroups: [] }],
              },
            ],
            users: [],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '루트' })).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('사용자명 필터'), '공통');
      await userEvent.click(screen.getByRole('button', { name: '검색' }));

      await waitFor(() => {
        expect(screen.getByText('브랜치A')).toBeInTheDocument();
        expect(screen.getByText('브랜치B')).toBeInTheDocument();
        expect(screen.getAllByText('공통이름').length).toBe(2);
      });
    });
  });

  describe('req 20260408 grid search: TC-07 reset', () => {
    test('초기화 restores full user list visibility', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'D1',
            name: '팀',
            children: [],
            users: [
              { userId: 1, userName: '가', employeeNumber: '1', rank: '-', position: '-', permissionGroups: [] },
              { userId: 2, userName: '나', employeeNumber: '2', rank: '-', position: '-', permissionGroups: [] },
            ],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('사용자명 필터'), '가');
      await userEvent.click(screen.getByRole('button', { name: '검색' }));
      await waitFor(() => expect(screen.queryByText('나')).not.toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: '검색 초기화' }));
      await userEvent.click(screen.getByRole('button', { name: '모두 펼치기' }));

      await waitFor(() => {
        expect(screen.getByText('가')).toBeInTheDocument();
        expect(screen.getByText('나')).toBeInTheDocument();
      });
      expect(screen.getByLabelText('사용자명 필터')).toHaveValue('');
    });
  });

  describe('req 20260408 grid search: TC-08 TC-09 expand / collapse all', () => {
    test('모두 펼치기 expands expandable nodes; 모두 접기 clears expansion', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'P',
            name: '상위',
            children: [
              { code: 'C', name: '하위', children: [], users: [{ userId: 1, userName: 'U', employeeNumber: '1', rank: '-', position: '-', permissionGroups: [] }] },
            ],
            users: [],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '상위' })).toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: '모두 펼치기' }));
      await waitFor(() => expect(screen.getByRole('button', { name: 'U 사용자 삭제' })).toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: '모두 접기' }));
      await waitFor(() => expect(screen.queryByRole('button', { name: 'U 사용자 삭제' })).not.toBeInTheDocument());
    });
  });

  describe('req 20260408 grid search: TC-10 search then collapse then expand all', () => {
    test('recovers deterministic expanded state without error', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [
          {
            code: 'P',
            name: '상위',
            children: [
              { code: 'C', name: '하위', children: [], users: [{ userId: 1, userName: '특정', employeeNumber: '1', rank: '-', position: '-', permissionGroups: [] }] },
            ],
            users: [],
          },
        ],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByRole('button', { name: '상위' })).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('사용자명 필터'), '특정');
      await userEvent.click(screen.getByRole('button', { name: '검색' }));
      await waitFor(() => expect(screen.getByText('특정')).toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: '모두 접기' }));
      await userEvent.click(screen.getByRole('button', { name: '모두 펼치기' }));
      await waitFor(() => expect(screen.getByText('특정')).toBeInTheDocument());
    });
  });

  describe('req 20260408 grid search: TC-14 empty tree', () => {
    test('empty hierarchy disables filter actions and shows message', async () => {
      getUserPermissionHierarchy.mockResolvedValue({ data: [] });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('등록된 부서가 없습니다.')).toBeInTheDocument());

      expect(screen.getByRole('button', { name: '검색' })).toBeDisabled();
      expect(screen.getByRole('button', { name: '검색 초기화' })).toBeDisabled();
      expect(screen.getByRole('button', { name: '모두 펼치기' })).toBeDisabled();
    });
  });

  describe('req 20260408 TC-15 TC-16 TC-18 filter toolbar vs tree utilities', () => {
    test('검색 row holds three fields plus 검색 and 검색 초기화; 모두 펼치기 is small outlined MUI inside tree panel, not search panel', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ code: 'D1', name: '팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());

      const toolbar = document.querySelector('.user-management-v2-filter-toolbar-row');
      expect(toolbar).toBeTruthy();
      expect(within(toolbar).getByRole('button', { name: '검색' })).toBeInTheDocument();
      expect(within(toolbar).getByRole('button', { name: '검색 초기화' })).toBeInTheDocument();

      const searchPanel = document.querySelector('.user-management-v2-search-panel');
      const treeSection = document.querySelector('.user-permission-hierarchy-tree-section');
      expect(searchPanel).toBeTruthy();
      expect(treeSection).toBeTruthy();

      const expandAll = screen.getByRole('button', { name: '모두 펼치기' });
      expect(expandAll.className).toMatch(/MuiButton-outlined/);
      expect(expandAll.className).toMatch(/MuiButton-sizeSmall/);
      expect(expandAll.closest('.user-management-v2-search-panel')).toBeNull();
      expect(treeSection.contains(expandAll)).toBe(true);
    });
  });

  describe('HR/외부 프로비저닝 진입 경로', () => {
    test('인사정보 기반 등록 버튼·모달·ExternalProvisioning 진입은 노출되지 않는다', async () => {
      getUserPermissionHierarchy.mockResolvedValue({
        data: [{ id: 101, code: 'D1', name: '팀', children: [], users: [] }],
      });

      await renderUserManagement(adminUser);
      await waitFor(() => expect(screen.getByText('팀')).toBeInTheDocument());

      expect(screen.getByText('수동 부서 트리 편집과 직접 사용자 등록으로 계정을 관리합니다.')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '인사정보 기반 등록(전환 기간)' })).not.toBeInTheDocument();
      expect(screen.queryByTestId('external-provisioning')).not.toBeInTheDocument();
    });
  });
});
