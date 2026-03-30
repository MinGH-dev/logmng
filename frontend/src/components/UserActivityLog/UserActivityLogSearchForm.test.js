import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserActivityLogSearchForm from './UserActivityLogSearchForm';

const defaultProps = () => ({
  onSearch: jest.fn(),
  loading: false,
  initialServerDate: '2026-03-13',
  isSelfScope: true,
  departmentList: [],
  selfContext: {
    department: '개발부',
    username: '홍길동',
    userId: 20260001,
  },
  actionTypeOptions: [
    { value: '', label: '전체' },
    { value: 'LOGIN', label: '로그인' },
  ],
});

describe('UserActivityLogSearchForm (TC-11, TC-12)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('TC-11: activity type dropdown lists options from props (API-shaped labels)', () => {
    const actionTypeOptions = [
      { value: '', label: '전체' },
      { value: 'LOGIN', label: '로그인' },
      { value: 'PERMISSION_GROUP_UPDATE', label: '권한 그룹 수정' },
    ];
    render(
      <UserActivityLogSearchForm
        {...defaultProps()}
        actionTypeOptions={actionTypeOptions}
        actionTypesLoading={false}
      />,
    );

    expect(screen.getByRole('option', { name: '전체' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '로그인' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '권한 그룹 수정' })).toBeInTheDocument();
  });

  test('TC-12: selecting a type and submitting sends actionType in search payload', async () => {
    const onSearch = jest.fn();
    const actionTypeOptions = [
      { value: '', label: '전체' },
      { value: 'SEARCH', label: '검색' },
    ];
    render(
      <UserActivityLogSearchForm
        {...defaultProps()}
        onSearch={onSearch}
        actionTypeOptions={actionTypeOptions}
      />,
    );

    await userEvent.selectOptions(screen.getByLabelText('액션 타입'), 'SEARCH');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(onSearch).toHaveBeenCalledWith(
      expect.objectContaining({
        actionType: 'SEARCH',
      }),
    );
  });
});
