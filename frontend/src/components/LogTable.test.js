import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import LogTable, {
  formatLogTableTime,
  normalizeLogTableKeywords,
  coerceKeywordMatchFlag,
  resolveKeywordMatchField,
} from './LogTable';

describe('normalizeLogTableKeywords', () => {
  test('comma-separated string splits and trims', () => {
    expect(normalizeLogTableKeywords('a, b , c')).toEqual(['a', 'b', 'c']);
  });

  test('single term string becomes one keyword', () => {
    expect(normalizeLogTableKeywords('LOCAL-PB')).toEqual(['LOCAL-PB']);
  });

  test('array pass-through with trim and empty drop', () => {
    expect(normalizeLogTableKeywords([' x ', 'y', ''])).toEqual(['x', 'y']);
  });

  test('null and empty yield empty array', () => {
    expect(normalizeLogTableKeywords(null)).toEqual([]);
    expect(normalizeLogTableKeywords('')).toEqual([]);
  });

  test('single-element array is one keyword (wireframe searchParams.keywords)', () => {
    expect(normalizeLogTableKeywords(['LOCAL-PB'])).toEqual(['LOCAL-PB']);
  });
});

describe('coerceKeywordMatchFlag', () => {
  test('JSON boolean and string true/false', () => {
    expect(coerceKeywordMatchFlag(true)).toBe(true);
    expect(coerceKeywordMatchFlag(false)).toBe(false);
    expect(coerceKeywordMatchFlag('true')).toBe(true);
    expect(coerceKeywordMatchFlag('TRUE')).toBe(true);
    expect(coerceKeywordMatchFlag('1')).toBe(true);
    expect(coerceKeywordMatchFlag('false')).toBe(false);
    expect(coerceKeywordMatchFlag('')).toBe(false);
  });

  test('unknown types are false', () => {
    expect(coerceKeywordMatchFlag(undefined)).toBe(false);
    expect(coerceKeywordMatchFlag(null)).toBe(false);
    expect(coerceKeywordMatchFlag({})).toBe(false);
    expect(coerceKeywordMatchFlag('maybe')).toBe(false);
  });
});

describe('resolveKeywordMatchField', () => {
  test('prefers snake_case when both are set', () => {
    const log = { keyword_match_data: false, keywordMatchData: true };
    expect(resolveKeywordMatchField(log, 'keyword_match_data', 'keywordMatchData')).toBe(false);
  });

  test('uses camelCase when snake_case is undefined', () => {
    const log = { keywordMatchData: true };
    expect(resolveKeywordMatchField(log, 'keyword_match_data', 'keywordMatchData')).toBe(true);
  });

  test('snake_case null is used (not skipped for camel)', () => {
    const log = { keyword_match_data: null, keywordMatchData: true };
    expect(resolveKeywordMatchField(log, 'keyword_match_data', 'keywordMatchData')).toBe(null);
  });

  test('undefined when neither key is set', () => {
    expect(resolveKeywordMatchField({}, 'keyword_match_data', 'keywordMatchData')).toBeUndefined();
  });
});

const pbFepSvgLog = (id) => ({
  id,
  log_type: 'PB',
  log_time: '2024-01-01T10:00:00',
  tr_code: 'TR',
  login_id: 'u1',
  msg_code: 'M',
  /** Empty so stream falls back to `data` when req/res absent (matches backend preview priority). */
  bmsg: '',
  log_ch_cd: 'c',
  send_recv: 'S',
  src_ip: '1.1.1.1',
  dest_ip: '2.2.2.2',
  app_id: 'a',
  data: 'd',
});

const baseProps = {
  logs: [pbFepSvgLog(1)],
  loading: false,
  sortConfig: { key: 'log_time', direction: 'desc' },
  sortCriteria: [{ key: 'log_time', direction: 'desc' }],
  onSort: jest.fn(),
  currentPage: 1,
  totalPages: 1,
  totalCount: 1,
  onPageChange: jest.fn(),
  pageSize: 25,
  onPageSizeChange: jest.fn(),
  layoutVariant: 'pb-fep-svg',
};

describe('formatLogTableTime (PB FEP log_time lexical)', () => {
  test('formats 20-digit yyyyMMddHHmmssSSSSSS with microseconds', () => {
    expect(formatLogTableTime('20260415143025123456')).toBe('2026-04-15 14:30:25.123456');
  });

  test('formats legacy 14-digit yyyyMMddHHmmss without fractional part', () => {
    expect(formatLogTableTime('20260415143025')).toBe('2026-04-15 14:30:25');
  });

  test('renders 20-digit log_time in pb-fep-svg table cell', () => {
    render(
      <LogTable
        {...baseProps}
        logs={[{ ...pbFepSvgLog(1), log_time: '20260415143025123456' }]}
      />
    );
    expect(screen.getByText('2026-04-15 14:30:25.123456')).toBeInTheDocument();
  });
});

describe('LogTable PB FEP controlled expansion', () => {
  test('onRowExpandChange receives manualCollapse true when user collapses a row', () => {
    const onRowExpandChange = jest.fn();
    render(
      <LogTable
        {...baseProps}
        expandedRowKeys={new Set(['PB-1'])}
        onRowExpandChange={onRowExpandChange}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '전문 접기' }));
    expect(onRowExpandChange).toHaveBeenCalledTimes(1);
    expect(onRowExpandChange).toHaveBeenCalledWith(expect.any(Set), { manualCollapse: true });
    const arg = onRowExpandChange.mock.calls[0][0];
    expect(arg.has('PB-1')).toBe(false);
  });

  test('onRowExpandChange receives manualCollapse false when user expands a row', () => {
    const onRowExpandChange = jest.fn();
    render(
      <LogTable
        {...baseProps}
        expandedRowKeys={new Set()}
        onRowExpandChange={onRowExpandChange}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '전문 펼치기' }));
    expect(onRowExpandChange).toHaveBeenCalledTimes(1);
    expect(onRowExpandChange).toHaveBeenCalledWith(expect.any(Set), { manualCollapse: false });
    const arg = onRowExpandChange.mock.calls[0][0];
    expect(arg.has('PB-1')).toBe(true);
  });
});

describe('PB FEP keyword highlight (ImageLog parity)', () => {
  test('TC-01: expanded stream wraps literal keyword in mark', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'prefix KEYWORD suffix' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['KEYWORD']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const marks = container.querySelectorAll('.stream-line mark');
    expect(marks.length).toBeGreaterThan(0);
    expect(Array.from(marks).some((m) => m.textContent.includes('KEYWORD'))).toBe(true);
  });

  test('TC-02: multiple keywords (OR) — second term matches when first does not', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'line with only SECONDTERM here' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['notpresent', 'SECONDTERM']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const marks = container.querySelectorAll('.stream-line mark');
    expect(Array.from(marks).some((m) => m.textContent.includes('SECONDTERM'))).toBe(true);
  });

  test('TC-03: quoted bracket value gets encrypted-highlight when keyword inside brackets', () => {
    const logs = [{ ...pbFepSvgLog(1), data: '{"payload":"[ENC_INSIDE]"}' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['ENC_INSIDE']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const enc = container.querySelectorAll('mark.encrypted-highlight');
    expect(enc.length).toBeGreaterThan(0);
    expect(
      Array.from(enc).some((el) => el.textContent.includes('[ENC_INSIDE]'))
    ).toBe(true);
  });

  test('TC-04: heuristic encrypted-highlight when keyword substring only inside quoted bracket (no hasEncryptedMatch*)', () => {
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: '{"payload":"[ENC_CIPHER_NO_PLAIN_SUBSTR_0001]"}',
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['PLAIN']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const enc = container.querySelectorAll('mark.encrypted-highlight');
    expect(enc.length).toBeGreaterThan(0);
    expect(
      Array.from(enc).some((el) => el.textContent.includes('[ENC_CIPHER_NO_PLAIN_SUBSTR_0001]'))
    ).toBe(true);
  });

  test('TC-06: collapsed wireframe row highlights tr_code and bmsg cells', () => {
    const logs = [
      {
        ...pbFepSvgLog(1),
        tr_code: 'TRHIT',
        bmsg: 'body-HIT-msg',
      },
    ];
    const { container } = render(
      <LogTable {...baseProps} logs={logs} keywords={['HIT']} expandedRowKeys={new Set()} />
    );
    const row = container.querySelector('tbody tr.log-row-pb-fep-svg');
    const cells = row.querySelectorAll('td');
    const trCell = cells[1];
    const bmsgCell = cells[4];
    expect(trCell.querySelectorAll('mark').length).toBeGreaterThan(0);
    expect(bmsgCell.querySelectorAll('mark').length).toBeGreaterThan(0);
  });

  test('TC-06 (legacy layout): collapsed row highlights visible string cells', () => {
    const legacyLog = {
      id: 2,
      log_type: 'PB',
      log_time: '20260415143025',
      tr_code: 'LEGACYHIT',
      user_id: 'u',
      status_code: '200',
      error_message: 'err',
      device_type: 'd',
      log_type: 'L',
      ip_address: '1.1.1.1',
      session_id: 's',
      response_time: 10,
      data: '{}',
    };
    const { container } = render(
      <LogTable
        {...baseProps}
        layoutVariant="default"
        logs={[legacyLog]}
        keywords={['LEGACYHIT']}
        expandedRowKeys={new Set()}
      />
    );
    const row = container.querySelector('tbody tr');
    const cells = row.querySelectorAll('td');
    expect(cells[1].querySelectorAll('mark').length).toBeGreaterThan(0);
  });
});

describe('PB FEP stream DATA full-line keyword emphasis', () => {
  test('TC-FL-01: matching pb-fep-svg stream line has stream-line--keyword-hit', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'prefix KEYWORD suffix' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['KEYWORD']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const hitLines = container.querySelectorAll('.stream-line--keyword-hit');
    expect(hitLines.length).toBe(1);
    expect(hitLines[0].querySelectorAll('mark').length).toBeGreaterThan(0);
  });

  test('TC-FL-02: non-matching line lacks full-line emphasis class', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'plain text no match here' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['ZZZNOTFOUND']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(0);
  });

  test('TC-FL-03: multi-line payload — only matching logical line has full-line class', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'line one\nMATCHLINE\nline three' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['MATCHLINE']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line').length).toBe(3);
    const hits = container.querySelectorAll('.stream-line--keyword-hit');
    expect(hits.length).toBe(1);
    expect(hits[0].textContent).toContain('MATCHLINE');
  });

  test('TC-FL-04: legacy layout — per-line wrappers; full-line class only on matching line', () => {
    const legacyLog = {
      id: 99,
      log_type: 'PB',
      log_time: '20260415143025',
      tr_code: 'X',
      user_id: 'u',
      status_code: '200',
      error_message: 'err',
      device_type: 'd',
      log_io_cd: 'L',
      ip_address: '1.1.1.1',
      session_id: 's',
      response_time: 10,
      data: 'aaa\nBBBKEY\nccc',
    };
    const { container } = render(
      <LogTable
        {...baseProps}
        layoutVariant="default"
        logs={[legacyLog]}
        keywords={['BBBKEY']}
        expandedRowKeys={new Set(['PB-99'])}
      />
    );
    expect(container.querySelectorAll('.stream-line').length).toBe(3);
    const hits = container.querySelectorAll('.stream-line--keyword-hit');
    expect(hits.length).toBe(1);
    expect(hits[0].textContent).toContain('BBBKEY');
  });

  test('TC-FL-05: empty line between lines — blank line not emphasized unless match', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'has KEY\n\nafter blank' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['KEY']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const lines = container.querySelectorAll('.stream-line');
    expect(lines.length).toBe(3);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(1);
    expect(lines[1].classList.contains('stream-line--keyword-hit')).toBe(false);
    expect(lines[1].textContent).toBe('');
  });

  test('TC-FL-06: regression — prior PB FEP highlight TCs still hold', () => {
    const logs = [{ ...pbFepSvgLog(1), data: 'prefix KEYWORD suffix' }];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['KEYWORD']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const marks = container.querySelectorAll('.stream-line mark');
    expect(marks.length).toBeGreaterThan(0);
    expect(Array.from(marks).some((m) => m.textContent.includes('KEYWORD'))).toBe(true);
  });

  test('TC-FL-07: regression — truncated summary `data` without keyword; full `response_data` drives stream marks', () => {
    const longRes = `${'x'.repeat(300)} LOCAL-PB ${'y'.repeat(50)}`;
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: 'short summary no keyword',
        response_data: longRes,
        request_data: '',
        keyword_match_response_data: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['LOCAL-PB']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    const marks = container.querySelectorAll('.stream-line mark');
    expect(marks.length).toBeGreaterThan(0);
    expect(Array.from(marks).some((m) => m.textContent.includes('LOCAL-PB'))).toBe(true);
  });

  test('TC-KF-05: decrypt-only server flag — every stream line gets stream-line--keyword-hit when no mark', () => {
    const ciphertext = 'QUJDRDEyMzQ1Njc4OQ==';
    const twoLinePayload = `${ciphertext}\nsecond-line-cipher`;
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: twoLinePayload,
        response_data: twoLinePayload,
        request_data: '',
        keyword_match_response_data: true,
        keyword_match_data: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['LOCAL-PB']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    const hitLines = container.querySelectorAll('.stream-line--keyword-hit');
    expect(hitLines.length).toBe(2);
  });

  test('TC-KF-06: literal keyword in stream still per-line emphasis; flags do not force bulk lines', () => {
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: 'line one\nline two LOCAL-PB here\nline three',
        keyword_match_response_data: true,
        keyword_match_data: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['LOCAL-PB']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBeGreaterThan(0);
    const hits = container.querySelectorAll('.stream-line--keyword-hit');
    expect(hits.length).toBe(1);
    expect(hits[0].textContent).toContain('LOCAL-PB');
  });

  test('TC-KF-07b: keyword_match_data as string "true" — decrypt-only bulk hint applies', () => {
    const ciphertext = 'QUJDRDEyMzQ1Njc4OQ==';
    const twoLinePayload = `${ciphertext}\nsecond-line-cipher`;
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: twoLinePayload,
        response_data: twoLinePayload,
        request_data: '',
        keyword_match_response_data: true,
        keyword_match_data: 'true',
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['LOCAL-PB']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(2);
  });

  test('TC-KF-07: keywords prop as string — decrypt-only full-line emphasis still applies', () => {
    const ciphertext = 'QUJDRDEyMzQ1Njc4OQ==';
    const twoLinePayload = `${ciphertext}\nsecond-line-cipher`;
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: twoLinePayload,
        response_data: twoLinePayload,
        request_data: '',
        keyword_match_response_data: true,
        keyword_match_data: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords="LOCAL-PB"
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(2);
  });

  test('TC-KF-08a: request_data null — response stream + strict keyword_match_response_data', () => {
    const cipher = 'NOPMARKCIPHER999==';
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: null,
        request_data: null,
        response_data: cipher,
        keyword_match_response_data: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['ANYKW']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(1);
  });

  test('TC-KF-08b: request_data empty string — same as null for stream source', () => {
    const cipher = 'NOPMARKCIPHER999==';
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: '',
        request_data: '',
        response_data: cipher,
        keyword_match_response_data: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['ANYKW']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(1);
  });

  test('TC-KF-09: camelCase-only keywordMatch* — decrypt-only bulk hint (gateway-normalized row)', () => {
    const ciphertext = 'QUJDRDEyMzQ1Njc4OQ==';
    const twoLinePayload = `${ciphertext}\nsecond-line-cipher`;
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: twoLinePayload,
        response_data: twoLinePayload,
        request_data: '',
        keyword_match_response_data: undefined,
        keyword_match_data: undefined,
        keywordMatchResponseData: true,
        keywordMatchData: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['LOCAL-PB']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(2);
  });

  test('TC-KF-09a: camelCase keywordMatchResponseData only — response stream decrypt-only lines', () => {
    const cipher = 'NOPMARKCIPHER999==';
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: null,
        request_data: null,
        response_data: cipher,
        keyword_match_response_data: undefined,
        keywordMatchResponseData: true,
      },
    ];
    const { container } = render(
      <LogTable
        {...baseProps}
        logs={logs}
        keywords={['ANYKW']}
        expandedRowKeys={new Set(['PB-1'])}
      />
    );
    expect(container.querySelectorAll('.stream-line mark').length).toBe(0);
    expect(container.querySelectorAll('.stream-line--keyword-hit').length).toBe(1);
  });

  test('TC-KF-09b: camelCase keywordMatchBmsg — full-line bmsg emphasis when no plaintext mark', () => {
    const ciphertext = 'QUJDRDEyMzQ1Njc4OQ==';
    const logs = [
      {
        ...pbFepSvgLog(1),
        data: 'plain',
        bmsg: ciphertext,
        keyword_match_bmsg: undefined,
        keywordMatchBmsg: true,
      },
    ];
    const { container } = render(
      <LogTable {...baseProps} logs={logs} keywords={['LOCAL-PB']} expandedRowKeys={new Set()} />
    );
    const row = container.querySelector('tbody tr.log-row-pb-fep-svg');
    const bmsgCell = row.querySelectorAll('td')[4];
    expect(bmsgCell.classList.contains('pb-fep-bmsg--keyword-hit')).toBe(true);
  });
});
