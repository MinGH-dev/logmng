import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdvancedSearchForm from './AdvancedSearchForm';

jest.mock('../config/runtimeApi', () => ({
  getApiBaseUrl: () => 'http://localhost:9200/api',
}));

/** Matches FieldMetadataService.getJavaFwImglogFieldMetadata() (eight canonical fields). */
const JAVA_FW_IMGLOG_METADATA_FIELDS = [
  { name: 'insert_time', label: '삽입 시간', operatorsAllowed: ['>=', '<=', '>', '<', '='] },
  { name: 'application', label: '애플리케이션', operatorsAllowed: [':', '=', 'IN', 'NOT IN'] },
  { name: 'servicegroup', label: '서비스 그룹', operatorsAllowed: [':', '=', 'IN', 'NOT IN'] },
  { name: 'service', label: '서비스', operatorsAllowed: [':', '=', 'IN', 'NOT IN'] },
  { name: 'status', label: '상태', operatorsAllowed: [':', '=', 'IN', 'NOT IN'] },
  { name: 'guid', label: 'GUID', operatorsAllowed: [':', '='] },
  { name: 'datastring', label: '데이터 문자열', operatorsAllowed: [':', '~'] },
  { name: 'headerstring', label: '헤더 문자열', operatorsAllowed: [':', '~'] },
];

function mockFieldsFetch(fields, delayMs = 0) {
  global.fetch = jest.fn((url) => {
    if (String(url).includes('/log-types/java_fw_imglog/fields')) {
      return new Promise((resolve) => {
        setTimeout(
          () =>
            resolve({
              ok: true,
              json: async () => ({ success: true, data: fields }),
            }),
          delayMs
        );
      });
    }
    return Promise.resolve({
      ok: true,
      json: async () => ({ success: true, data: [] }),
    });
  });
}

describe('AdvancedSearchForm', () => {
  const logType = { id: 'java_fw_imglog' };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('TC-01: focus with empty input shows searchable field names from metadata', async () => {
    mockFieldsFetch(JAVA_FW_IMGLOG_METADATA_FIELDS);
    render(<AdvancedSearchForm logType={logType} onSearch={jest.fn()} />);
    const input = screen.getByPlaceholderText(/필드명을 입력/);
    await userEvent.click(input);

    await waitFor(() => {
      expect(screen.getByTestId('advanced-search-field-picker')).toBeInTheDocument();
    });
    for (const f of JAVA_FW_IMGLOG_METADATA_FIELDS) {
      expect(screen.getByTestId(`field-picker-item-${f.name}`)).toBeInTheDocument();
    }
  });

  test('TC-02: selecting status inserts canonical fragment and places caret for value typing', async () => {
    mockFieldsFetch(JAVA_FW_IMGLOG_METADATA_FIELDS);
    render(<AdvancedSearchForm logType={logType} onSearch={jest.fn()} />);
    const input = screen.getByPlaceholderText(/필드명을 입력/);
    await userEvent.click(input);
    await waitFor(() => expect(screen.getByTestId('field-picker-item-status')).toBeInTheDocument());

    await userEvent.click(screen.getByTestId('field-picker-item-status'));

    expect(input).toHaveValue('status:');
    expect(input.selectionStart).toBe(7);
    expect(input.selectionEnd).toBe(7);

    await userEvent.type(input, 'input');
    expect(input).toHaveValue('status:input');
  });

  test('TC-03: selecting insert_time inserts first operator fragment and caret after operator', async () => {
    mockFieldsFetch(JAVA_FW_IMGLOG_METADATA_FIELDS);
    render(<AdvancedSearchForm logType={logType} onSearch={jest.fn()} />);
    const input = screen.getByPlaceholderText(/필드명을 입력/);
    await userEvent.click(input);
    await waitFor(() => expect(screen.getByTestId('field-picker-item-insert_time')).toBeInTheDocument());

    await userEvent.click(screen.getByTestId('field-picker-item-insert_time'));

    expect(input).toHaveValue('insert_time >= ');
    const len = 'insert_time >= '.length;
    expect(input.selectionStart).toBe(len);
    expect(input.selectionEnd).toBe(len);
  });

  test('TC-04: delayed metadata load does not crash; list appears after load', async () => {
    mockFieldsFetch(JAVA_FW_IMGLOG_METADATA_FIELDS, 30);
    render(<AdvancedSearchForm logType={logType} onSearch={jest.fn()} />);
    const input = screen.getByPlaceholderText(/필드명을 입력/);
    await userEvent.click(input);
    expect(screen.getByTestId('advanced-search-field-picker-loading')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByTestId('advanced-search-field-picker-loading')).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getByTestId('advanced-search-field-picker')).toBeInTheDocument();
    });
    expect(
      within(screen.getByTestId('advanced-search-field-picker')).getAllByRole('option')
    ).toHaveLength(JAVA_FW_IMGLOG_METADATA_FIELDS.length);
  });

  test('TC-05: pick field, type value, search sends filters with correct field/operator/value', async () => {
    mockFieldsFetch(JAVA_FW_IMGLOG_METADATA_FIELDS);
    const onSearch = jest.fn();
    render(<AdvancedSearchForm logType={logType} onSearch={onSearch} />);
    const input = screen.getByPlaceholderText(/필드명을 입력/);
    await userEvent.click(input);
    await waitFor(() => expect(screen.getByTestId('field-picker-item-status')).toBeInTheDocument());
    await userEvent.click(screen.getByTestId('field-picker-item-status'));
    await userEvent.type(input, 'error');
    await userEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(onSearch).toHaveBeenCalled();
    const payload = onSearch.mock.calls[0][0];
    expect(payload.filters).toEqual(
      expect.arrayContaining([expect.objectContaining({ field: 'status', operator: ':', value: 'error' })])
    );
    expect(payload.logType).toBe('java_fw_imglog');
  });

  test('TC-07: Escape closes field picker without changing tokens', async () => {
    mockFieldsFetch(JAVA_FW_IMGLOG_METADATA_FIELDS);
    render(<AdvancedSearchForm logType={logType} onSearch={jest.fn()} />);
    const input = screen.getByPlaceholderText(/필드명을 입력/);
    await userEvent.click(input);
    await waitFor(() => expect(screen.getByTestId('advanced-search-field-picker')).toBeInTheDocument());

    await userEvent.keyboard('{Escape}');

    await waitFor(() => {
      expect(screen.queryByTestId('advanced-search-field-picker')).not.toBeInTheDocument();
    });
    expect(input).toHaveValue('');
  });
});
