import React from 'react';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ImageLogTable from './ImageLogTable';
import { DECRYPTION_NOT_APPROVED_MESSAGE } from '../utils/security';

jest.mock('../utils/logger', () => ({
  debug: jest.fn(),
  info: jest.fn(),
  error: jest.fn(),
}));

const defaultProps = {
  logs: [],
  loading: false,
  sortConfig: { key: 'insert_time', direction: 'desc' },
  onSort: jest.fn(),
  currentPage: 1,
  totalPages: 1,
  totalCount: 0,
  onPageChange: jest.fn(),
  pageSize: 20,
  onPageSizeChange: jest.fn(),
  keywords: [],
  searchParams: {},
  hasDecryptPermission: true,
};

describe('ImageLogTable (req 20260318 decryption-allowed store and decrypt UI)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.alert = jest.fn();
  });

  describe('TC-01: No encrypted data → no decrypt button', () => {
    test('row with no encrypted data shows "-" and no decrypt button', () => {
      const logs = [
        {
          guid: 'guid-plain',
          status: 'OK',
          insert_time: '2026-03-18 10:00:00',
          application: 'app1',
          servicegroup: 'sg1',
          service: 'svc1',
          datastring: '{"key":"value"}',
          headerstring: '{"h":"v"}',
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
        />
      );
      const table = container.querySelector('.log-table');
      expect(table).toBeInTheDocument();
      const rows = table.querySelectorAll('tbody tr');
      expect(rows.length).toBe(1);
      const decryptCell = rows[0].querySelector('td.decrypt-action-cell');
      expect(decryptCell).toBeInTheDocument();
      expect(decryptCell).toHaveTextContent('-');
      const decryptBtn = within(decryptCell).queryByRole('button', { name: /복호화/ });
      expect(decryptBtn).not.toBeInTheDocument();
    });
  });

  describe('TC-02 (edge): Plain datastring/headerstring but data/header non-empty', () => {
    test('row with plain datastring/headerstring and non-empty data/header shows "-" and no decrypt button', () => {
      const logs = [
        {
          guid: 'guid-plain-with-data',
          status: 'OK',
          insert_time: '2026-03-18 10:00:00',
          application: 'app1',
          servicegroup: 'sg1',
          service: 'svc1',
          datastring: '{"id":"plain","name":"","age":0}',
          headerstring: '{"h":"v"}',
          data: 'plain',
          header: 'plain',
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
        />
      );
      const decryptCell = container.querySelector('td.decrypt-action-cell');
      expect(decryptCell).toBeInTheDocument();
      expect(decryptCell).toHaveTextContent('-');
      const decryptBtn = within(decryptCell).queryByRole('button', { name: /복호화/ });
      expect(decryptBtn).not.toBeInTheDocument();
    });
  });

  describe('TC-02: Encrypted data but GUID not in allowed (or expired) → dimmed button, click shows message', () => {
    test('dimmed decrypt button has class decrypt-btn--not-allowed and click shows approval message', async () => {
      const logs = [
        {
          guid: 'guid-encrypted-not-allowed',
          status: 'OK',
          insert_time: '2026-03-18 10:00:00',
          application: 'app1',
          servicegroup: 'sg1',
          service: 'svc1',
          datastring: '{"key":"[encrypted-value]"}',
          headerstring: '{}',
        },
      ];
      const decryptionAllowed = {
        validUntil: '2026-12-31T23:59:59',
        guids: ['other-guid'],
      };
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          decryptionAllowed={decryptionAllowed}
        />
      );
      const decryptCell = container.querySelector('td.decrypt-action-cell');
      const dimmedBtn = within(decryptCell).getByRole('button', { name: /복호화 \(승인 필요\)/ });
      expect(dimmedBtn).toHaveClass('decrypt-btn--not-allowed');
      await userEvent.click(dimmedBtn);
      expect(global.alert).toHaveBeenCalledWith(DECRYPTION_NOT_APPROVED_MESSAGE);
    });
  });

  describe('TC-03: Encrypted data, GUID in allowed, validUntil future → normal button, click calls decrypt API', () => {
    test('normal decrypt button calls POST /api/logs/decrypt with guid and status', async () => {
      const logs = [
        {
          guid: 'guid-allowed',
          status: 'OK',
          insert_time: '2026-03-18 10:00:00',
          application: 'app1',
          servicegroup: 'sg1',
          service: 'svc1',
          datastring: '{"key":"[encrypted]"}',
          headerstring: '{}',
        },
      ];
      const decryptionAllowed = {
        validUntil: '2026-12-31T23:59:59',
        guids: ['guid-allowed'],
        allowedRows: [{ guid: 'guid-allowed', status: 'OK' }],
      };
      let capturedUrl;
      let capturedBody;
      global.fetch = jest.fn((url, options) => {
        capturedUrl = url;
        capturedBody = options?.body ? JSON.parse(options.body) : null;
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({
            success: true,
            data: { decrypted_datastring: '{}', decrypted_headerstring: '{}' },
          }),
        });
      });

      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          decryptionAllowed={decryptionAllowed}
        />
      );
      const decryptCell = container.querySelector('td.decrypt-action-cell');
      const decryptBtn = within(decryptCell).getByRole('button', { name: '복호화' });
      expect(decryptBtn).not.toHaveClass('decrypt-btn--not-allowed');
      await userEvent.click(decryptBtn);
      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          expect.stringContaining('/logs/decrypt/java_fw_imglog'),
          expect.objectContaining({
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
          })
        );
        expect(capturedBody).toEqual({ guid: 'guid-allowed', status: 'OK' });
      });
    });

    test('same guid but status not in allowedRows → dimmed (composite key)', async () => {
      const logs = [
        {
          guid: 'guid-allowed',
          status: 'OK',
          insert_time: '2026-03-18 10:00:00',
          application: 'app1',
          servicegroup: 'sg1',
          service: 'svc1',
          datastring: '{"key":"[encrypted]"}',
          headerstring: '{}',
        },
      ];
      const decryptionAllowed = {
        validUntil: '2026-12-31T23:59:59',
        guids: ['guid-allowed'],
        allowedRows: [{ guid: 'guid-allowed', status: 'OTHER' }],
      };
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          decryptionAllowed={decryptionAllowed}
        />
      );
      const decryptCell = container.querySelector('td.decrypt-action-cell');
      const dimmedBtn = within(decryptCell).getByRole('button', { name: /복호화 \(승인 필요\)/ });
      expect(dimmedBtn).toHaveClass('decrypt-btn--not-allowed');
    });
  });
});
