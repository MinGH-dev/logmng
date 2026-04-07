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
  externalDepartmentId: 'D1',
  jobTitle: 'Staff',
};

describe('ExternalProvisioning (TC-F04)', () => {
  beforeEach(() => {
    jest
      .spyOn(provisioningService, 'searchExternalEmployees')
      .mockResolvedValue({
        success: true,
        data: {
          items: [employeeRow],
          pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
        },
      });
    jest.spyOn(provisioningService, 'searchExternalDepartments').mockResolvedValue({
      success: true,
      data: {
        items: [],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 0 },
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

  test('search employees and provision calls API', async () => {
    const onProvisioned = jest.fn();
    render(<ExternalProvisioning onProvisioned={onProvisioned} />);

    await userEvent.click(screen.getByRole('button', { name: '외부 직원 검색 실행' }));

    await waitFor(() => {
      expect(provisioningService.searchExternalEmployees).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1, pageSize: 20 })
      );
    });

    await waitFor(() => {
      expect(screen.getByText('홍길동')).toBeInTheDocument();
    });

    const radios = screen.getAllByRole('radio');
    expect(radios.length).toBeGreaterThan(0);
    await userEvent.click(radios[0]);

    await userEvent.click(screen.getByRole('button', { name: '선택 직원 등록' }));

    await waitFor(() => {
      expect(provisioningService.provisionUserFromExternalEmployee).toHaveBeenCalledWith(
        expect.objectContaining({
          externalEmployeeId: 'E1',
        })
      );
      expect(onProvisioned).toHaveBeenCalled();
    });
  });
});
