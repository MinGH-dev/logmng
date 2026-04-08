import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HrSyncPocPreview, { POC_DISABLED_MESSAGE } from './HrSyncPocPreview';
import * as hrSyncPocService from '../../services/hrSyncPocService';

jest.mock('../../services/hrSyncPocService');
jest.mock('../../utils/logger', () => ({
  __esModule: true,
  default: { debug: jest.fn(), error: jest.fn(), info: jest.fn() },
}));

const enabledConfig = {
  success: true,
  data: { pocEnabled: true, defaultMode: 'PREVIEW_ONLY', applyEnabled: false },
};

describe('HrSyncPocPreview', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    hrSyncPocService.fetchSnapshots.mockResolvedValue({ success: true, data: { snapshots: [] } });
    hrSyncPocService.fetchEmployees.mockResolvedValue({
      success: true,
      data: {
        snapshotId: 'x',
        employees: [],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 0 },
      },
    });
  });

  test('shows PoC disabled when config returns pocEnabled false', async () => {
    hrSyncPocService.getHrSyncPocConfig.mockResolvedValue({
      success: true,
      data: { pocEnabled: false, defaultMode: 'PREVIEW_ONLY', applyEnabled: false },
    });
    render(<HrSyncPocPreview />);
    expect(await screen.findByText(POC_DISABLED_MESSAGE)).toBeInTheDocument();
    expect(hrSyncPocService.postHrSyncPocPreview).not.toHaveBeenCalled();
    expect(hrSyncPocService.fetchSnapshots).not.toHaveBeenCalled();
  });

  test('shows PoC disabled when config returns HTTP 403 POC_DISABLED', async () => {
    const err = new Error('forbidden');
    err.status = 403;
    err.code = 'POC_DISABLED';
    hrSyncPocService.getHrSyncPocConfig.mockRejectedValue(err);
    render(<HrSyncPocPreview />);
    expect(await screen.findByText(POC_DISABLED_MESSAGE)).toBeInTheDocument();
    expect(hrSyncPocService.fetchSnapshots).not.toHaveBeenCalled();
  });

  test('shows permission error when config returns HTTP 403 FORBIDDEN (not PoC disabled)', async () => {
    const err = new Error('forbidden');
    err.status = 403;
    err.code = 'FORBIDDEN';
    hrSyncPocService.getHrSyncPocConfig.mockRejectedValue(err);
    render(<HrSyncPocPreview />);
    expect(await screen.findByText('권한이 없습니다.')).toBeInTheDocument();
    expect(screen.queryByText(POC_DISABLED_MESSAGE)).not.toBeInTheDocument();
    expect(hrSyncPocService.fetchSnapshots).not.toHaveBeenCalled();
  });

  test('when enabled, loads snapshots and shows run preview control', async () => {
    hrSyncPocService.getHrSyncPocConfig.mockResolvedValue(enabledConfig);
    render(<HrSyncPocPreview />);
    await waitFor(() => {
      expect(hrSyncPocService.fetchSnapshots).toHaveBeenCalled();
    });
    expect(await screen.findByRole('button', { name: /run preview/i })).toBeInTheDocument();
  });

  test('TC-08: selecting a snapshot loads first page of employees into the table', async () => {
    hrSyncPocService.getHrSyncPocConfig.mockResolvedValue(enabledConfig);
    hrSyncPocService.fetchSnapshots.mockResolvedValue({
      success: true,
      data: {
        snapshots: [
          { snapshotId: 'poc-snap-20260408-A', employeeCount: 2 },
          { snapshotId: 'poc-snap-20260408-B', employeeCount: 3 },
        ],
      },
    });
    hrSyncPocService.fetchEmployees.mockResolvedValue({
      success: true,
      data: {
        snapshotId: 'poc-snap-20260408-B',
        employees: [
          {
            displayName: 'User B1',
            jobTitle: 'Dev',
            departmentKey: 'EXT-D1',
            departmentName: 'Dept B',
            active: true,
            employeeNumber: '20261001',
          },
        ],
        pagination: { currentPage: 1, totalPages: 1, totalCount: 1 },
      },
    });

    render(<HrSyncPocPreview />);
    await waitFor(() => {
      expect(hrSyncPocService.fetchSnapshots).toHaveBeenCalled();
    });

    const select = await screen.findByLabelText('PoC 스냅샷 선택');
    await userEvent.selectOptions(select, 'poc-snap-20260408-B');

    await waitFor(() => {
      expect(hrSyncPocService.fetchEmployees).toHaveBeenCalledWith('poc-snap-20260408-B', 1, 20);
    });
    expect(await screen.findByText('User B1')).toBeInTheDocument();
    expect(screen.getByText('Dev')).toBeInTheDocument();
    expect(screen.getByText('20261001')).toBeInTheDocument();
  });

  test('TC-06: after successful Run preview, summary panel shows classification counts and meta fields', async () => {
    hrSyncPocService.getHrSyncPocConfig.mockResolvedValue(enabledConfig);
    hrSyncPocService.postHrSyncPocPreview.mockResolvedValue({
      success: true,
      data: {
        previewId: 'poc-preview-test-1',
        snapshotId: 'poc-snap-20260408-A',
        riskTier: 'LOW',
        upstreamGateStatus: 'OPEN',
        messageCode: 'OK',
        classificationCounts: {
          TRANSFER: 2,
          NEW_HIRE: 1,
          RESIGNED: 0,
          UNCHANGED: 10,
          PROFILE_UPDATE_NON_SECURITY: 3,
          CONFLICT: 0,
          ORPHAN: 1,
        },
      },
    });

    render(<HrSyncPocPreview />);
    await waitFor(() => {
      expect(hrSyncPocService.fetchSnapshots).toHaveBeenCalled();
    });

    await userEvent.click(await screen.findByRole('button', { name: /run preview/i }));

    const panel = await screen.findByTestId('hr-sync-poc-preview-summary');
    expect(panel).toBeInTheDocument();
    expect(within(panel).getByRole('heading', { name: '분류 요약' })).toBeInTheDocument();
    const table = await within(panel).findByRole('table');
    const transferRow = within(table).getByRole('row', { name: /전환\(이동\)/ });
    expect(within(transferRow).getByRole('cell', { name: '2' })).toBeInTheDocument();
    expect(within(panel).getByText('poc-preview-test-1')).toBeInTheDocument();
    expect(within(panel).getByText('poc-snap-20260408-A')).toBeInTheDocument();
    const meta = panel.querySelector('.hr-sync-poc-preview-summary-meta');
    expect(meta).toBeTruthy();
    expect(within(meta).getByText('LOW')).toBeInTheDocument();
    expect(within(meta).getByText('OPEN')).toBeInTheDocument();
    expect(within(meta).getByText('OK')).toBeInTheDocument();
    expect(within(panel).getByText('응답 원문 (JSON)')).toBeInTheDocument();
  });

  test('TC-07: preview API error shows inline message in summary panel', async () => {
    hrSyncPocService.getHrSyncPocConfig.mockResolvedValue(enabledConfig);
    const err = new Error('upstream not ready');
    err.status = 503;
    err.code = 'HR_SYNC_POC_PREVIEW_FAILED';
    hrSyncPocService.postHrSyncPocPreview.mockRejectedValue(err);

    render(<HrSyncPocPreview />);
    await waitFor(() => {
      expect(hrSyncPocService.fetchSnapshots).toHaveBeenCalled();
    });

    await userEvent.click(await screen.findByRole('button', { name: /run preview/i }));

    const panel = await screen.findByTestId('hr-sync-poc-preview-summary');
    const alert = await within(panel).findByRole('alert');
    expect(alert).toHaveTextContent('upstream not ready');
  });

  test('TC-07: success false shows inline error in summary panel', async () => {
    hrSyncPocService.getHrSyncPocConfig.mockResolvedValue(enabledConfig);
    hrSyncPocService.postHrSyncPocPreview.mockResolvedValue({
      success: false,
      code: 'VALIDATION_ERROR',
      error: '스냅샷이 필요합니다.',
    });

    render(<HrSyncPocPreview />);
    await waitFor(() => {
      expect(hrSyncPocService.fetchSnapshots).toHaveBeenCalled();
    });

    await userEvent.click(await screen.findByRole('button', { name: /run preview/i }));

    const panel = await screen.findByTestId('hr-sync-poc-preview-summary');
    expect(await within(panel).findByRole('alert')).toHaveTextContent('스냅샷이 필요합니다.');
  });
});
