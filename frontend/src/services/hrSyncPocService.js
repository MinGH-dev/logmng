/**
 * HR Sync PoC (preview-only). specs/hr-sync-poc.spec.yaml, docs/contract.md
 */

import {
  DEFAULT_EMPLOYEES_PAGE_SIZE,
  MAX_EMPLOYEES_PAGE_SIZE,
  MIN_EMPLOYEES_PAGE_SIZE,
} from '../config/hrSyncPocUi';
import { getApiBaseUrl } from '../config/runtimeApi';

const pocBase = () => `${getApiBaseUrl()}/hr-sync/poc`;

const fetchWithCreds = async (url, options = {}) => {
  const response = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
  return response;
};

const parseJsonSafe = async (response) => {
  let result = {};
  try {
    result = await response.json();
  } catch {
    result = {};
  }
  if (!response.ok) {
    const msg = result.error || `HTTP ${response.status}`;
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    err.payload = result;
    throw err;
  }
  return result;
};

/**
 * GET /api/hr-sync/poc/config
 * @returns {Promise<{ success: boolean, data: { pocEnabled: boolean, defaultMode: string, applyEnabled: boolean } }>}
 */
export async function getHrSyncPocConfig() {
  const response = await fetchWithCreds(`${pocBase()}/config`, { method: 'GET' });
  return parseJsonSafe(response);
}

/**
 * POST /api/hr-sync/poc/preview
 * @param {{ snapshotId?: string, ingestRunId?: string }} body
 */
export async function postHrSyncPocPreview(body = {}) {
  const payload = {};
  if (body.snapshotId != null && String(body.snapshotId).trim() !== '') {
    payload.snapshotId = String(body.snapshotId).trim();
  }
  if (body.ingestRunId != null && String(body.ingestRunId).trim() !== '') {
    payload.ingestRunId = String(body.ingestRunId).trim();
  }
  const response = await fetchWithCreds(`${pocBase()}/preview`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  return parseJsonSafe(response);
}

/**
 * GET /api/hr-sync/poc/snapshots
 * @returns {Promise<{ success: boolean, data: { snapshots: Array<{ snapshotId: string, label?: string|null, employeeCount?: number, maxImportedAt?: string|null }> } }>}
 */
export async function fetchSnapshots() {
  const response = await fetchWithCreds(`${pocBase()}/snapshots`, { method: 'GET' });
  return parseJsonSafe(response);
}

/**
 * GET /api/hr-sync/poc/snapshots/{snapshotId}/employees?page=&size=
 * @param {string} snapshotId
 * @param {number} [page=1]
 * @param {number} [size=DEFAULT_EMPLOYEES_PAGE_SIZE]
 */
export async function fetchEmployees(snapshotId, page = 1, size = DEFAULT_EMPLOYEES_PAGE_SIZE) {
  const sid = String(snapshotId ?? '').trim();
  if (!sid) {
    const err = new Error('snapshotId is required');
    err.code = 'INVALID_INPUT';
    throw err;
  }
  const p = Math.max(1, Number(page) || 1);
  const s = Math.min(
    MAX_EMPLOYEES_PAGE_SIZE,
    Math.max(MIN_EMPLOYEES_PAGE_SIZE, Number(size) || DEFAULT_EMPLOYEES_PAGE_SIZE),
  );
  const q = new URLSearchParams({ page: String(p), size: String(s) });
  const url = `${pocBase()}/snapshots/${encodeURIComponent(sid)}/employees?${q.toString()}`;
  const response = await fetchWithCreds(url, { method: 'GET' });
  return parseJsonSafe(response);
}
