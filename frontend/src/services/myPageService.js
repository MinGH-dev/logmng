/**
 * My page — GET /api/auth/me, POST /api/auth/me/password
 * specs/my-page-password.spec.yaml, docs/contract.md
 */

import { getApiBaseUrl } from '../config/runtimeApi';

const parseJsonSafe = async (response) => {
  try {
    return await response.json();
  } catch {
    return {};
  }
};

/**
 * @returns {Promise<{ success: boolean, data?: { user: object } }>}
 */
export async function fetchAuthMe() {
  const response = await fetch(`${getApiBaseUrl()}/auth/me`, { credentials: 'include' });
  const result = await parseJsonSafe(response);
  if (!response.ok) {
    const err = new Error(
      (typeof result.error === 'string' && result.error.trim()) ||
        (typeof result.message === 'string' && result.message.trim()) ||
        `HTTP ${response.status}`
    );
    err.status = response.status;
    err.code = result.code != null ? String(result.code) : undefined;
    err.payload = result;
    throw err;
  }
  return result;
}

/**
 * @param {{ currentPassword: string, newPassword: string, confirmNewPassword: string }} body
 * @returns {Promise<object>}
 */
export async function postOwnPassword(body) {
  const response = await fetch(`${getApiBaseUrl()}/auth/me/password`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      currentPassword: body.currentPassword,
      newPassword: body.newPassword,
      confirmNewPassword: body.confirmNewPassword,
    }),
  });
  const result = await parseJsonSafe(response);
  if (!response.ok) {
    const err = new Error(
      (typeof result.error === 'string' && result.error.trim()) ||
        (typeof result.message === 'string' && result.message.trim()) ||
        `HTTP ${response.status}`
    );
    err.status = response.status;
    err.code = result.code != null ? String(result.code) : undefined;
    err.payload = result;
    throw err;
  }
  return result;
}
