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

describe('ImageLogTable (req 20260330 Pretty per guid+status)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.alert = jest.fn();
  });

  const jsonLogsSameGuid = [
    {
      guid: 'shared-guid',
      status: 'S1',
      insert_time: '2026-03-30 10:00:00',
      application: 'app1',
      servicegroup: 'sg1',
      service: 'svc1',
      datastring: '{"a":1}',
      headerstring: '{"h":1}',
    },
    {
      guid: 'shared-guid',
      status: 'S2',
      insert_time: '2026-03-30 10:01:00',
      application: 'app1',
      servicegroup: 'sg1',
      service: 'svc1',
      datastring: '{"b":2}',
      headerstring: '{"h":2}',
    },
  ];

  /** TC-01: Pretty on row 1 does not affect row 2 */
  test('TC-01: same guid different status — Pretty toggle affects only that row', async () => {
    const { container } = render(
      <ImageLogTable {...defaultProps} logs={jsonLogsSameGuid} totalCount={2} />
    );
    const rows = container.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);

    const prettyBtn0 = within(rows[0].querySelector('td.pretty-action-cell')).getByRole('button', {
      name: /Pretty 출력/,
    });
    const prettyBtn1 = within(rows[1].querySelector('td.pretty-action-cell')).getByRole('button', {
      name: /Pretty 출력/,
    });
    expect(prettyBtn0).toHaveTextContent('Pretty');
    expect(prettyBtn1).toHaveTextContent('Pretty');

    await userEvent.click(prettyBtn0);

    expect(rows[0].querySelectorAll('td.tr-data-cell.pretty-mode').length).toBeGreaterThan(0);
    expect(rows[1].querySelectorAll('td.tr-data-cell.pretty-mode').length).toBe(0);
    expect(
      within(rows[0].querySelector('td.pretty-action-cell')).getByRole('button', { name: /Pretty 출력/ })
    ).toHaveTextContent('Pretty OFF');
    expect(
      within(rows[1].querySelector('td.pretty-action-cell')).getByRole('button', { name: /Pretty 출력/ })
    ).toHaveTextContent('Pretty');
  });

  /** TC-02: both rows can be Pretty ON independently */
  test('TC-02: Pretty on row 2 after row 1 — both rows independent', async () => {
    const { container } = render(
      <ImageLogTable {...defaultProps} logs={jsonLogsSameGuid} totalCount={2} />
    );
    const rows = container.querySelectorAll('tbody tr');
    const prettyBtn0 = within(rows[0].querySelector('td.pretty-action-cell')).getByRole('button', {
      name: /Pretty 출력/,
    });
    const prettyBtn1 = within(rows[1].querySelector('td.pretty-action-cell')).getByRole('button', {
      name: /Pretty 출력/,
    });

    await userEvent.click(prettyBtn0);
    await userEvent.click(prettyBtn1);

    expect(rows[0].querySelectorAll('td.tr-data-cell.pretty-mode').length).toBeGreaterThan(0);
    expect(rows[1].querySelectorAll('td.tr-data-cell.pretty-mode').length).toBeGreaterThan(0);
    expect(
      within(rows[0].querySelector('td.pretty-action-cell')).getByRole('button', { name: /Pretty 출력/ })
    ).toHaveTextContent('Pretty OFF');
    expect(
      within(rows[1].querySelector('td.pretty-action-cell')).getByRole('button', { name: /Pretty 출력/ })
    ).toHaveTextContent('Pretty OFF');
  });

  /** TC-03: decrypt remains per guid+status when two rows share guid */
  test('TC-03: decrypt only the first row — second row still shows 복호화', async () => {
    const logs = [
      {
        guid: 'g-dup',
        status: 'A',
        insert_time: '2026-03-30 10:00:00',
        application: 'app1',
        servicegroup: 'sg1',
        service: 'svc1',
        datastring: '{"k":"[enc-a]"}',
        headerstring: '{}',
      },
      {
        guid: 'g-dup',
        status: 'B',
        insert_time: '2026-03-30 10:01:00',
        application: 'app1',
        servicegroup: 'sg1',
        service: 'svc1',
        datastring: '{"k":"[enc-b]"}',
        headerstring: '{}',
      },
    ];
    const decryptionAllowed = {
      validUntil: '2026-12-31T23:59:59',
      guids: ['g-dup'],
      allowedRows: [
        { guid: 'g-dup', status: 'A' },
        { guid: 'g-dup', status: 'B' },
      ],
    };

    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            success: true,
            data: { decrypted_datastring: '{"a":1}', decrypted_headerstring: '{}' },
          }),
      })
    );

    const { container } = render(
      <ImageLogTable
        {...defaultProps}
        logs={logs}
        totalCount={2}
        decryptionAllowed={decryptionAllowed}
      />
    );
    const rows = container.querySelectorAll('tbody tr');
    const decryptBtn0 = within(rows[0].querySelector('td.decrypt-action-cell')).getByRole('button', {
      name: '복호화',
    });
    await userEvent.click(decryptBtn0);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(
        within(rows[0].querySelector('td.decrypt-action-cell')).getByRole('button', { name: '복호화 해제' })
      ).toBeInTheDocument();
    });
    expect(
      within(rows[1].querySelector('td.decrypt-action-cell')).getByRole('button', { name: '복호화' })
    ).toBeInTheDocument();
  });
});
