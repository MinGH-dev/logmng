import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import * as provisioningService from '../../services/provisioningService';
import ExternalProvisioning from './ExternalProvisioning';

const employeeRow = {
  externalEmployeeId: 'E1',
  sourceSystem: 'HR',
  employeeNumber: 'N1',
  displayName: '홍길동',
  departmentName: '기획팀',
  externalDepartmentId: 'D1',
  jobTitle: 'Staff',
};

describe('ExternalProvisioning (TC-F04)', () => {
  beforeEach(() => {
    jest.spyOn(provisioningService, 'searchExternalEmployees').mockResolvedValue({
      success: true,
      data: {
        items: [employeeRow],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
      },
    });
    jest.spyOn(provisioningService, 'provisionUserFromExternalEmployee').mockResolvedValue({
      success: true,
      data: { userId: 20260002, username: 'newuser' },
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('unified 직원 검색 calls API with filters and provision flow', async () => {
    const onProvisioned = jest.fn();
    render(<ExternalProvisioning onProvisioned={onProvisioned} />);

    await userEvent.type(screen.getByLabelText('부서'), '기획');
    await userEvent.type(screen.getByLabelText('사용자 ID'), 'N1');
    await userEvent.type(screen.getByLabelText('직원명'), '홍');

    await userEvent.click(screen.getByRole('button', { name: '직원 검색 실행' }));

    await waitFor(() => {
      expect(provisioningService.searchExternalEmployees).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 1,
          pageSize: 20,
          departmentName: '기획',
          employeeNumber: 'N1',
          keyword: '홍',
        })
      );
    });

    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument();
      expect(screen.getByText('기획팀')).toBeInTheDocument();
    });

    const radios = screen.getAllByRole('radio');
    expect(radios.length).toBeGreaterThan(0);
    await userEvent.click(radios[0]);

    await userEvent.type(screen.getByLabelText(/등록 사유/), 'HR 연동 등록');

    await userEvent.click(screen.getByRole('button', { name: '선택 직원 등록' }));

    await waitFor(() => {
      expect(provisioningService.provisionUserFromExternalEmployee).toHaveBeenCalledWith(
        expect.objectContaining({
          externalEmployeeId: 'E1',
          changeReason: 'HR 연동 등록',
        })
      );
      expect(onProvisioned).toHaveBeenCalled();
      expect(screen.getByText(/등록되었습니다\. 사용자 ID: 20260002/)).toBeInTheDocument();
    });
  });

  test('success message includes HR user id and app id when API returns employeeNumber', async () => {
    jest.spyOn(provisioningService, 'provisionUserFromExternalEmployee').mockResolvedValue({
      success: true,
      data: { userId: 20270001, employeeNumber: '20261001', username: 'newuser' },
    });

    render(<ExternalProvisioning />);

    await userEvent.click(screen.getByRole('button', { name: '직원 검색 실행' }));
    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument();
    });

    await userEvent.click(screen.getAllByRole('radio')[0]);
    await userEvent.type(screen.getByLabelText(/등록 사유/), '신규 입사');
    await userEvent.click(screen.getByRole('button', { name: '선택 직원 등록' }));

    await waitFor(() => {
      expect(screen.getByText(/사용자 ID\(인사\): 20261001/)).toBeInTheDocument();
      expect(screen.getByText(/앱 내부 ID: 20270001/)).toBeInTheDocument();
    });
  });

  test('provisioned row shows 등록됨 badge and disables selection for that row', async () => {
    jest.spyOn(provisioningService, 'searchExternalEmployees').mockResolvedValue({
      success: true,
      data: {
        items: [
          {
            ...employeeRow,
            provisioned: true,
            provisionedUsername: 'already',
            provisionedAppUserId: 99,
          },
        ],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
      },
    });

    render(<ExternalProvisioning />);

    await userEvent.click(screen.getByRole('button', { name: '직원 검색 실행' }));

    await waitFor(() => {
      expect(screen.getByText('등록됨')).toBeInTheDocument();
      expect(screen.getByText(/already/)).toBeInTheDocument();
    });

    const registerBtn = screen.getByRole('button', { name: '선택 직원 등록' });
    expect(registerBtn).toBeDisabled();

    const radio = screen.getByRole('radio', { name: /이미 등록됨/ });
    expect(radio).toBeDisabled();
  });

  test('409 conflict message appends existingUsername and existingAppUserId when present', async () => {
    const conflict = new Error('duplicate');
    conflict.status = 409;
    conflict.payload = {
      existingUsername: 'dupuser',
      existingAppUserId: 20260099,
    };
    jest.spyOn(provisioningService, 'provisionUserFromExternalEmployee').mockRejectedValue(conflict);

    render(<ExternalProvisioning />);

    await userEvent.click(screen.getByRole('button', { name: '직원 검색 실행' }));
    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument();
    });

    await userEvent.click(screen.getAllByRole('radio')[0]);
    await userEvent.type(screen.getByLabelText(/등록 사유/), '재시도');
    await userEvent.click(screen.getByRole('button', { name: '선택 직원 등록' }));

    await waitFor(() => {
      expect(
        screen.getByText(/이미 인사정보로 등록된 직원입니다\./)
      ).toBeInTheDocument();
      expect(screen.getByText(/기존 사용자명: dupuser/)).toBeInTheDocument();
      expect(screen.getByText(/기존 앱 사용자 ID: 20260099/)).toBeInTheDocument();
    });
  });

  test('TC-10: 등록 사유 없이는 API를 호출하지 않음', async () => {
    render(<ExternalProvisioning />);

    await userEvent.click(screen.getByRole('button', { name: '직원 검색 실행' }));
    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument();
    });

    await userEvent.click(screen.getAllByRole('radio')[0]);
    await userEvent.click(screen.getByRole('button', { name: '선택 직원 등록' }));

    await waitFor(() => {
      expect(provisioningService.provisionUserFromExternalEmployee).not.toHaveBeenCalled();
      expect(screen.getByText('등록 사유를 입력하세요.')).toBeInTheDocument();
    });
  });
});
