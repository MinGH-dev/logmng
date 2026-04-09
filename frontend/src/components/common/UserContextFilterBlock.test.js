import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UserContextFilterBlock from './UserContextFilterBlock';

describe('UserContextFilterBlock', () => {
  test('renders editable controls for team/all scope', async () => {
    const onChange = jest.fn();

    render(
      <UserContextFilterBlock
        blockLabel="사용자"
        mode="editable"
        departmentList={['개발부']}
        values={{ department: '', username: '', userId: '' }}
        onChange={onChange}
        idPrefix="editable-user-context"
        compact
      />,
    );

    expect(screen.getByRole('option', { name: '전체' })).toBeInTheDocument();
    expect(screen.getByLabelText('부서')).toBeInTheDocument();
    expect(screen.getByLabelText('사용자명 (최대 5자)')).toBeInTheDocument();
    expect(screen.getByLabelText('사용자 ID (숫자 8자리)')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('부서'), '개발부');
    expect(onChange).toHaveBeenCalledWith('department', '개발부');
  });

  test('renders locked self-context without editable widening controls', () => {
    render(
      <UserContextFilterBlock
        blockLabel="요청자"
        mode="locked"
        lockedValues={{
          department: '개발부',
          username: '홍길동',
          userId: 20260001,
          employeeNumber: 'EMP-001',
        }}
        values={{ department: '', username: '', userId: '' }}
        onChange={jest.fn()}
        idPrefix="locked-user-context"
        compact
      />,
    );

    expect(screen.getByDisplayValue('개발부')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('홍길동')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('EMP-001')).toHaveAttribute('readonly');
    expect(screen.queryByRole('option', { name: '전체' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('사용자명 (최대 5자)')).not.toBeInTheDocument();
    expect(screen.getByLabelText('사용자명')).toBeInTheDocument();
    expect(screen.getByLabelText('사용자 ID')).toBeInTheDocument();
  });
});
