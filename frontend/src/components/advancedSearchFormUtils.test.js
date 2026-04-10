import {
  dedupeFieldsByName,
  getCanonicalFieldFragment,
  parseContext,
  parseInlineFilterToPayload,
  buildFiltersFromAdvancedInput,
} from './advancedSearchFormUtils';

describe('advancedSearchFormUtils', () => {
  const sampleMeta = [
    { name: 'status', label: '상태', operatorsAllowed: [':', '=', 'IN', 'NOT IN'] },
    { name: 'insert_time', label: '삽입 시간', operatorsAllowed: ['>=', '<=', '>', '<', '='] },
    { name: 'status', label: 'dup', operatorsAllowed: [':'] },
  ];

  test('dedupeFieldsByName keeps first occurrence per name', () => {
    const d = dedupeFieldsByName(sampleMeta);
    expect(d).toHaveLength(2);
    expect(d[0].label).toBe('상태');
    expect(d[1].name).toBe('insert_time');
  });

  test('getCanonicalFieldFragment uses colon when first operator is colon', () => {
    expect(getCanonicalFieldFragment({ name: 'status', operatorsAllowed: [':', '='] })).toBe('status:');
  });

  test('getCanonicalFieldFragment uses first relational operator and trailing space for insert_time', () => {
    expect(getCanonicalFieldFragment({ name: 'insert_time', operatorsAllowed: ['>=', '<=', '>', '<', '='] })).toBe(
      'insert_time >= '
    );
  });

  test('parseContext: inline field:value yields value context', () => {
    const ctx = parseContext('application:foo', [], sampleMeta);
    expect(ctx.context).toBe('value');
    expect(ctx.field).toBe('application');
    expect(ctx.prefix).toBe('foo');
  });

  test('parseContext: insert_time >= <value> yields value context', () => {
    const meta = [{ name: 'insert_time', operatorsAllowed: ['>=', '<=', '>', '<', '='] }];
    const ctx = parseContext('insert_time >= 2024-01-01', [], meta);
    expect(ctx.context).toBe('value');
    expect(ctx.field).toBe('insert_time');
    expect(ctx.prefix).toBe('2024-01-01');
  });

  test('parseInlineFilterToPayload colon form', () => {
    const meta = [{ name: 'status', operatorsAllowed: [':', '='] }];
    expect(parseInlineFilterToPayload('status:input', meta)).toEqual({
      field: 'status',
      operator: ':',
      value: 'input',
    });
  });

  test('parseInlineFilterToPayload insert_time relational', () => {
    const meta = [{ name: 'insert_time', operatorsAllowed: ['>=', '<=', '>', '<', '='] }];
    expect(parseInlineFilterToPayload('insert_time >= 2024-01-01 00:00:00', meta)).toEqual({
      field: 'insert_time',
      operator: '>=',
      value: '2024-01-01 00:00:00',
    });
  });

  test('buildFiltersFromAdvancedInput uses inline when no tokens', () => {
    const meta = [{ name: 'status', operatorsAllowed: [':', '='] }];
    const filters = buildFiltersFromAdvancedInput({
      tokens: [],
      inputValue: 'status:error',
      fieldMetadata: meta,
    });
    expect(filters).toEqual([{ field: 'status', operator: ':', value: 'error' }]);
  });
});
