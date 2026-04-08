/**
 * PoC User Management (UM v2 clone) — read-only + migrate stub.
 * Base: /api/hr-sync/poc/user-mgmt. specs/hr-sync-poc.spec.yaml §4.5–4.7.
 */

import {
  DEFAULT_EMPLOYEES_PAGE_SIZE,
  MAX_EMPLOYEES_PAGE_SIZE,
  MIN_EMPLOYEES_PAGE_SIZE,
} from '../config/hrSyncPocUi';
import { getApiBaseUrl } from '../config/runtimeApi';

const pocUserMgmtBase = () => `${getApiBaseUrl()}/hr-sync/poc/user-mgmt`;

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

/** @param {unknown} err */
export function isPocUserMgmtUnauthorized(err) {
  return err?.status === 401 || err?.code === 'UNAUTHORIZED';
}

/** @param {unknown} err */
export function isPocUserMgmtDisabled(err) {
  return err?.code === 'POC_DISABLED';
}

/**
 * GET /api/hr-sync/poc/user-mgmt/replica-departments/tree
 * @param {string} [sourceSystem=HR_SAMPLE]
 */
export async function fetchReplicaDepartmentTree(sourceSystem = 'HR_SAMPLE') {
  const ss = String(sourceSystem ?? 'HR_SAMPLE').trim() || 'HR_SAMPLE';
  const q = new URLSearchParams({ sourceSystem: ss });
  const response = await fetchWithCreds(`${pocUserMgmtBase()}/replica-departments/tree?${q}`, {
    method: 'GET',
  });
  return parseJsonSafe(response);
}

/**
 * GET /api/hr-sync/poc/user-mgmt/replica-users
 * @param {{
 *   sourceSystem?: string,
 *   snapshotId?: string|null,
 *   departmentKey?: string|null,
 *   page?: number,
 *   size?: number,
 * }} params
 */
export async function fetchReplicaUsers(params = {}) {
  const sourceSystem = String(params.sourceSystem ?? 'HR_SAMPLE').trim() || 'HR_SAMPLE';
  const page = Math.max(1, Number(params.page) || 1);
  const size = Math.min(
    MAX_EMPLOYEES_PAGE_SIZE,
    Math.max(MIN_EMPLOYEES_PAGE_SIZE, Number(params.size) || DEFAULT_EMPLOYEES_PAGE_SIZE),
  );
  const q = new URLSearchParams({
    sourceSystem,
    page: String(page),
    size: String(size),
  });
  if (params.snapshotId != null && String(params.snapshotId).trim() !== '') {
    q.set('snapshotId', String(params.snapshotId).trim());
  }
  if (params.departmentKey != null && String(params.departmentKey).trim() !== '') {
    q.set('departmentKey', String(params.departmentKey).trim());
  }
  const response = await fetchWithCreds(`${pocUserMgmtBase()}/replica-users?${q}`, { method: 'GET' });
  return parseJsonSafe(response);
}

/**
 * POST /api/hr-sync/poc/user-mgmt/actions/migrate-preview
 * @returns {Promise<{ success: boolean, data: { persisted: boolean, messageCode: string } }>}
 */
export async function postMigratePreview(body = null) {
  const response = await fetchWithCreds(`${pocUserMgmtBase()}/actions/migrate-preview`, {
    method: 'POST',
    body: body && typeof body === 'object' ? JSON.stringify(body) : JSON.stringify({}),
  });
  return parseJsonSafe(response);
}
