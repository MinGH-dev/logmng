/**
 * Build activity-type filter options and label map from API or fallback `{ code, label }[]`.
 */

/**
 * @param {{ code: string, label: string }[]} items - non-empty codes from API or fallback (no "전체" row)
 * @returns {{ value: string, label: string }[]} select options with leading "전체"
 */
export function toActionTypeSelectOptions(items) {
  const list = Array.isArray(items) ? items : [];
  return [
    { value: '', label: '전체' },
    ...list.map(({ code, label }) => ({
      value: code,
      label: label != null && String(label).trim() !== '' ? label : code,
    })),
  ];
}

/**
 * @param {{ code: string, label: string }[]} items
 * @returns {Record<string, string>}
 */
export function toActionTypeLabelMap(items) {
  const map = {};
  const list = Array.isArray(items) ? items : [];
  list.forEach(({ code, label }) => {
    if (code == null || code === '') return;
    map[code] = label != null && String(label).trim() !== '' ? label : code;
  });
  return map;
}

/**
 * Table/detail display: known code → label; unknown → raw code (TC-13).
 * @param {string|null|undefined} actionType
 * @param {Record<string, string>} labelMap
 */
export function getActivityActionTypeLabel(actionType, labelMap) {
  if (actionType == null || actionType === '') return '-';
  const m = labelMap && typeof labelMap === 'object' ? labelMap : {};
  return m[actionType] ?? actionType;
}
