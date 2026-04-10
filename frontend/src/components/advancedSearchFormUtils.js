/**
 * Advanced search (java_fw_imglog) — shared parsing and filter building.
 * @see docs/requirements/20260409-advanced-search-field-name-picker-and-caret.md
 */

/** @param {string} s */
export function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Deduplicate field metadata by canonical `name` (stable order: first occurrence wins).
 * @param {Array<{ name?: string }>} fieldMetadata
 */
export function dedupeFieldsByName(fieldMetadata) {
  const map = new Map();
  for (const f of fieldMetadata || []) {
    if (f && f.name && !map.has(f.name)) {
      map.set(f.name, f);
    }
  }
  return Array.from(map.values());
}

/**
 * Option A inline fragment: `name:` when `:` is allowed; else `name <firstOperator> ` (trailing space for caret before value).
 * @param {{ name: string, operatorsAllowed?: string[] }} meta
 */
export function getCanonicalFieldFragment(meta) {
  if (!meta || !meta.name) return '';
  const ops = meta.operatorsAllowed && meta.operatorsAllowed.length > 0
    ? meta.operatorsAllowed
    : [':'];
  const first = ops[0];
  if (first === ':') {
    return `${meta.name}:`;
  }
  return `${meta.name} ${first} `;
}

/**
 * Parse inline `insert_time <op> <value>` after colon-style branch.
 * @param {string} trimmed
 * @param {Array<{ name: string, operatorsAllowed?: string[] }>} fieldMetadata
 */
function parseInlineFieldOperatorContext(trimmed, fieldMetadata) {
  for (const meta of fieldMetadata || []) {
    const frag = getCanonicalFieldFragment(meta);
    if (frag.endsWith(':')) continue;
    const fname = meta.name;
    const allowed = meta.operatorsAllowed || [];
    if (allowed.length === 0) continue;
    const opAlt = allowed.map(escapeRegex).join('|');
    const re = new RegExp(`^${escapeRegex(fname)}\\s+(${opAlt})\\s*(.*)$`, 'i');
    const mm = trimmed.match(re);
    if (mm) {
      return {
        context: 'value',
        field: fname,
        prefix: mm[2] ?? '',
        operator: mm[1],
      };
    }
  }
  return null;
}

/**
 * @param {string} input
 * @param {Array} tokens
 * @param {Array} fieldMetadata
 */
export function parseContext(input, tokens, fieldMetadata) {
  const trimmed = (input || '').trim();
  const lastSpaceIndex = trimmed.lastIndexOf(' ');
  const currentPart = lastSpaceIndex >= 0 ? trimmed.substring(lastSpaceIndex + 1) : trimmed;

  const lastToken = tokens.length > 0 ? tokens[tokens.length - 1] : null;
  if (lastToken && lastToken.type === 'field') {
    return {
      context: 'operator',
      field: lastToken.value,
      prefix: currentPart,
    };
  }
  if (lastToken && lastToken.type === 'operator') {
    return {
      context: 'value',
      field: lastToken.field,
      prefix: currentPart,
    };
  }

  const colonIdx = currentPart.indexOf(':');
  if (colonIdx !== -1) {
    const field = currentPart.slice(0, colonIdx).trim();
    const rest = currentPart.slice(colonIdx + 1);
    if (field) {
      return {
        context: 'value',
        field,
        prefix: rest,
      };
    }
  }

  const spaceOp = parseInlineFieldOperatorContext(trimmed, fieldMetadata);
  if (spaceOp) {
    return spaceOp;
  }

  return { context: 'field', prefix: currentPart };
}

/**
 * Parse a single inline condition from the input when there are no committed filter tokens.
 * Uses first-colon split for colon operators; relational form for insert_time.
 * @param {string} trimmed
 * @param {Array} fieldMetadata
 * @returns {{ field: string, operator: string, value: string } | null}
 */
export function parseInlineFilterToPayload(trimmed, fieldMetadata) {
  if (!trimmed) return null;

  const rel = trimmed.match(/^(insert_time)\s+(>=|<=|>|<|=)\s+(.+)$/i);
  if (rel) {
    return { field: 'insert_time', operator: rel[2], value: rel[3].trim() };
  }

  const idx = trimmed.indexOf(':');
  if (idx === -1) return null;
  const name = trimmed.slice(0, idx).trim().toLowerCase();
  const valuePart = trimmed.slice(idx + 1);
  if (!name) return null;
  const meta = (fieldMetadata || []).find((f) => f.name === name);
  if (!meta) return null;
  const ops = meta.operatorsAllowed || [];
  if (!ops.includes(':')) return null;
  if (valuePart === '') return null;
  return { field: name, operator: ':', value: valuePart };
}

/**
 * @param {{ tokens: Array, inputValue: string, fieldMetadata: Array }} state
 */
export function buildFiltersFromAdvancedInput({ tokens, inputValue, fieldMetadata }) {
  const filters = [];

  for (const token of tokens) {
    if (token.type === 'filter') {
      filters.push({
        field: token.field,
        operator: token.operator,
        value: token.value,
      });
    } else if (token.type === 'field') {
      const operator = token.operator || ':';
      const value = (inputValue || '').trim();
      if (value) {
        filters.push({
          field: token.value,
          operator,
          value,
        });
      }
    }
  }

  if (filters.length > 0) {
    return filters;
  }

  const inline = parseInlineFilterToPayload((inputValue || '').trim(), fieldMetadata);
  return inline ? [inline] : [];
}
