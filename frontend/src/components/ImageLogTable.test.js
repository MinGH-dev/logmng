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

/** ImageLogTable reads response via text() then JSON.parse — mocks must expose text() */
function mockDecryptOkResponse(data) {
  const body = JSON.stringify(data);
  return {
    ok: true,
    status: 200,
    text: () => Promise.resolve(body),
    json: () => Promise.resolve(JSON.parse(body)),
  };
}

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

describe('ImageLogTable DataTable layout (PB FEP wireframe parity)', () => {
  test('passes fill container and info-buttons-size pagination footer to DataTable', () => {
    const { container } = render(
      <ImageLogTable {...defaultProps} totalCount={3} />
    );
    expect(container.querySelector('.log-table-container.log-table-container--fill')).toBeInTheDocument();
    expect(container.querySelector('.pagination.pagination--info-buttons-size')).toBeInTheDocument();
  });
});

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
        return Promise.resolve(
          mockDecryptOkResponse({
            success: true,
            data: { decrypted_datastring: '{}', decrypted_headerstring: '{}' },
          })
        );
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

  describe('TC-04: decrypt API non-OK with JSON body shows message', () => {
    test('500 + DECRYPTION_FAILED alerts API message field', async () => {
      const logs = [
        {
          guid: 'guid-decrypt-fail',
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
        guids: ['guid-decrypt-fail'],
      };
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: false,
          status: 500,
          text: () =>
            Promise.resolve(
              JSON.stringify({
                code: 'DECRYPTION_FAILED',
                message: '복호화 처리에 실패했습니다.',
              })
            ),
        })
      );

      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          decryptionAllowed={decryptionAllowed}
        />
      );
      const decryptBtn = within(container.querySelector('td.decrypt-action-cell')).getByRole('button', {
        name: '복호화',
      });
      await userEvent.click(decryptBtn);
      await waitFor(() => {
        expect(global.alert).toHaveBeenCalledWith('복호화 처리에 실패했습니다.');
      });
    });

    test('400 with error string (no message) alerts error field', async () => {
      const logs = [
        {
          guid: 'guid-400',
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
        guids: ['guid-400'],
      };
      global.fetch = jest.fn(() =>
        Promise.resolve({
          ok: false,
          status: 400,
          text: () => Promise.resolve(JSON.stringify({ error: 'bad request detail' })),
        })
      );

      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          decryptionAllowed={decryptionAllowed}
        />
      );
      await userEvent.click(
        within(container.querySelector('td.decrypt-action-cell')).getByRole('button', { name: '복호화' })
      );
      await waitFor(() => {
        expect(global.alert).toHaveBeenCalledWith('bad request detail');
      });
    });
  });
});

describe('ImageLogTable (req 20260413 search vs decrypt display)', () => {
  test('grid uses datastring/headerstring for display, not search response decrypted_* / raw columns', () => {
    const logs = [
      {
        guid: 'g-search',
        status: 'OK',
        insert_time: '2026-04-13 10:00:00',
        application: 'app',
        servicegroup: 'sg',
        service: 'svc',
        datastring: '{"k":"[cipher-only]"}',
        headerstring: '{"h":"x"}',
        decrypted_datastring: '{"k":"LEAK_FROM_SEARCH"}',
        decrypted_headerstring: '{"h":"LEAK_HEADER"}',
        decrypted_data: 'BINARY_LEAK',
        decrypted_header: 'HEADER_LEAK',
        data: 'RAW_DATA',
        header: 'RAW_HEADER',
      },
    ];
    const { container } = render(<ImageLogTable {...defaultProps} logs={logs} totalCount={1} />);
    const row = container.querySelector('tbody tr');
    expect(row.textContent).toContain('[cipher-only]');
    expect(row.textContent).not.toContain('LEAK_FROM_SEARCH');
    expect(row.textContent).not.toContain('LEAK_HEADER');
    expect(row.textContent).not.toContain('BINARY_LEAK');
    expect(row.textContent).not.toContain('RAW_DATA');
  });

  describe('encrypted-region highlight metadata (API rename + legacy)', () => {
    test('hasEncryptedMatchDatastring true + keywords-only search wraps quoted bracket value in encrypted-highlight', () => {
      const logs = [
        {
          guid: 'g-enc-meta',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"k":"[cipher-blob]"}',
          headerstring: '{}',
          hasEncryptedMatchDatastring: true,
        },
      ];
      const { container } = render(
        <ImageLogTable {...defaultProps} logs={logs} totalCount={1} keywords={['kw']} searchParams={{}} />
      );
      const marks = container.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(Array.from(marks).some((el) => el.textContent.includes('[cipher-blob]'))).toBe(true);
    });

    test('legacy _datastring_has_encrypted_match still enables encrypted-highlight', () => {
      const logs = [
        {
          guid: 'g-legacy-ds',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"k":"[legacy-cipher]"}',
          headerstring: '{}',
          _datastring_has_encrypted_match: true,
        },
      ];
      const { container } = render(
        <ImageLogTable {...defaultProps} logs={logs} totalCount={1} keywords={['x']} searchParams={{}} />
      );
      expect(container.querySelector('mark.encrypted-highlight')).toBeInTheDocument();
    });

    test('hasEncryptedMatchHeaderstring true + keywords-only search wraps quoted bracket value in header cell', () => {
      const logs = [
        {
          guid: 'g-hdr-meta',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{}',
          headerstring: '{"h":"[hdr-cipher]"}',
          hasEncryptedMatchHeaderstring: true,
        },
      ];
      const { container } = render(
        <ImageLogTable {...defaultProps} logs={logs} totalCount={1} keywords={['q']} searchParams={{}} />
      );
      const row = container.querySelector('tbody tr');
      const cells = row.querySelectorAll('td');
      const headerCell = cells[7];
      expect(headerCell.querySelector('mark.encrypted-highlight')).toBeInTheDocument();
    });

    test('keywords-only LOCAL inside quoted bracket datastring: encrypted-highlight without field search or enc metadata', () => {
      const logs = [
        {
          guid: 'g-kw-in-bracket-ds',
          status: 'OK',
          insert_time: '2026-04-14 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"payload":"[ENC_OUT_PAYLOAD_LOCAL_0001]"}',
          headerstring: '{}',
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const cells = row.querySelectorAll('td');
      const dataCell = cells[6];
      const marks = dataCell.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(
        Array.from(marks).some((el) => el.textContent.includes('ENC_OUT_PAYLOAD_LOCAL_0001'))
      ).toBe(true);
    });

    test('keywords-only LOCAL inside quoted bracket headerstring: encrypted-highlight in header cell', () => {
      const logs = [
        {
          guid: 'g-kw-in-bracket-hdr',
          status: 'OK',
          insert_time: '2026-04-14 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{}',
          headerstring: '{"x":"[ENC_HDR_LOCAL_0001]"}',
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['local']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const cells = row.querySelectorAll('td');
      const headerCell = cells[7];
      const marks = headerCell.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(Array.from(marks).some((el) => el.textContent.includes('ENC_HDR_LOCAL_0001'))).toBe(
        true
      );
    });

    test('hasEncryptedMatchData without hasEncryptedMatchDatastring + LOCAL: quoted bracket in datastring cell gets encrypted-highlight', () => {
      const logs = [
        {
          guid: 'g-bin-data-match',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"k":"[E002X9ABC]"}',
          headerstring: '{}',
          hasEncryptedMatchData: true,
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const cells = row.querySelectorAll('td');
      const dataCell = cells[6];
      const marks = dataCell.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(
        Array.from(marks).some((el) => el.textContent.includes('[E002X9ABC]'))
      ).toBe(true);
    });

    test('hasEncryptedMatchData true, hasEncryptedMatchDatastring false + LOCAL: [ENC_ONLY_IN_DECRYPT] gets encrypted-highlight in datastring cell', () => {
      const logs = [
        {
          guid: 'LOCAL-DECRYPT-TST-IM-0001',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"payload":"[ENC_ONLY_IN_DECRYPT]"}',
          headerstring: '{}',
          hasEncryptedMatchData: true,
          hasEncryptedMatchDatastring: false,
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const dataCell = row.querySelectorAll('td')[6];
      const marks = dataCell.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(
        Array.from(marks).some((el) => el.textContent.includes('[ENC_ONLY_IN_DECRYPT]'))
      ).toBe(true);
    });

    test('keywords-only LOCAL: ciphertext without literal LOCAL (no enc metadata) + plain LOCAL in headerstring — data encrypted-highlight and header plain mark', () => {
      const logs = [
        {
          guid: 'LOCAL-DECRYPT-TST-IM-0001',
          status: 'OK',
          insert_time: '2026-04-14 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"payload":"[ENC_CIPHER_NO_LOCAL_SUBSTR_0001]"}',
          headerstring: '{"guid":"LOCAL-DECRYPT-TST-IM-0001","other":"v"}',
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const cells = row.querySelectorAll('td');
      const dataCell = cells[6];
      const headerCell = cells[7];
      const encMarks = dataCell.querySelectorAll('mark.encrypted-highlight');
      expect(encMarks.length).toBeGreaterThan(0);
      expect(
        Array.from(encMarks).some((el) =>
          el.textContent.includes('[ENC_CIPHER_NO_LOCAL_SUBSTR_0001]')
        )
      ).toBe(true);
      const plainMarks = headerCell.querySelectorAll('mark:not(.encrypted-highlight)');
      expect(plainMarks.length).toBeGreaterThan(0);
      expect(Array.from(plainMarks).some((el) => el.textContent.includes('LOCAL'))).toBe(true);
    });

    test('Pretty mode: hasEncryptedMatchData + LOCAL still wraps quoted bracket in datastring pre with encrypted-highlight', async () => {
      const logs = [
        {
          guid: 'g-pretty-bin-ds',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{"k":"[PRETTY_BIN_DS]"}',
          headerstring: '{}',
          hasEncryptedMatchData: true,
          hasEncryptedMatchDatastring: false,
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      await userEvent.click(
        within(row.querySelector('td.pretty-action-cell')).getByRole('button', { name: /Pretty 출력/ })
      );
      const dataCell = row.querySelectorAll('td')[6];
      const pre = dataCell.querySelector('pre.json-pretty-text');
      expect(pre).toBeInTheDocument();
      expect(pre.querySelectorAll('mark.encrypted-highlight').length).toBeGreaterThan(0);
    });

    test('hasEncryptedMatchHeader without hasEncryptedMatchHeaderstring + LOCAL: quoted bracket in header cell gets encrypted-highlight', () => {
      const logs = [
        {
          guid: 'g-bin-hdr-match',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{}',
          headerstring: '{"h":"[E002H9XYZ]"}',
          hasEncryptedMatchHeader: true,
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const cells = row.querySelectorAll('td');
      const headerCell = cells[7];
      const marks = headerCell.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(
        Array.from(marks).some((el) => el.textContent.includes('[E002H9XYZ]'))
      ).toBe(true);
    });

    test('hasEncryptedMatchHeader true, hasEncryptedMatchHeaderstring false + LOCAL: [ENC_ONLY_IN_DECRYPT_HDR] gets encrypted-highlight in header cell', () => {
      const logs = [
        {
          guid: 'g-hdr-bin-only',
          status: 'OK',
          insert_time: '2026-04-13 10:00:00',
          application: 'app',
          servicegroup: 'sg',
          service: 'svc',
          datastring: '{}',
          headerstring: '{"h":"[ENC_ONLY_IN_DECRYPT_HDR]"}',
          hasEncryptedMatchHeader: true,
          hasEncryptedMatchHeaderstring: false,
        },
      ];
      const { container } = render(
        <ImageLogTable
          {...defaultProps}
          logs={logs}
          totalCount={1}
          keywords={['LOCAL']}
          searchParams={{}}
        />
      );
      const row = container.querySelector('tbody tr');
      const headerCell = row.querySelectorAll('td')[7];
      const marks = headerCell.querySelectorAll('mark.encrypted-highlight');
      expect(marks.length).toBeGreaterThan(0);
      expect(
        Array.from(marks).some((el) => el.textContent.includes('[ENC_ONLY_IN_DECRYPT_HDR]'))
      ).toBe(true);
    });
  });

  /** TC-06: decrypt UI without permission — no alternate plaintext path */
  test('encrypted row with hasDecryptPermission false shows permission message, no decrypt button', () => {
    const logs = [
      {
        guid: 'g-no-perm',
        status: 'OK',
        insert_time: '2026-04-13 10:00:00',
        application: 'app',
        servicegroup: 'sg',
        service: 'svc',
        datastring: '{"k":"[enc]"}',
        headerstring: '{}',
      },
    ];
    const { container } = render(
      <ImageLogTable {...defaultProps} logs={logs} totalCount={1} hasDecryptPermission={false} />
    );
    const decryptCell = container.querySelector('td.decrypt-action-cell');
    expect(decryptCell).toHaveTextContent('복호화 권한이 없습니다.');
    expect(within(decryptCell).queryByRole('button', { name: /복호화/ })).not.toBeInTheDocument();
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
      Promise.resolve(
        mockDecryptOkResponse({
          success: true,
          data: { decrypted_datastring: '{"a":1}', decrypted_headerstring: '{}' },
        })
      )
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
