import { getApiBaseUrl } from '../config/runtimeApi';

/**
 * Resolves the full URL for GET/PUT /api/screen-display-labels when the runtime base may or may not include `/api`.
 * @param {string} baseUrl from getApiBaseUrl()
 * @returns {string}
 */
export function buildScreenDisplayLabelsUrl(baseUrl) {
  const base = String(baseUrl || '').replace(/\/+$/, '');
  if (base.endsWith('/api')) {
    return `${base}/screen-display-labels`;
  }
  return `${base}/api/screen-display-labels`;
}

function screenDisplayLabelsEndpoint() {
  return buildScreenDisplayLabelsUrl(getApiBaseUrl());
}

/** Backend ApiResponse.failure uses `error` (and `code`); some paths may still send `message`. */
function apiFailureMessage(json, fallback) {
  const apiMsg = json?.error || json?.message;
  return apiMsg || fallback;
}

/**
 * GET /api/screen-display-labels — session cookie. On failure returns [] (silent fallback).
 * @returns {Promise<Array<{ screenId: string, labelUser: string, labelAdmin?: string|null, parentGroupId?: string|null, sortOrder?: number }>>}
 */
export async function fetchScreenDisplayLabelsSilent() {
  try {
    const res = await fetch(screenDisplayLabelsEndpoint(), {
      credentials: 'include',
    });
    if (!res.ok) return [];
    const json = await res.json();
    if (!json?.success || !Array.isArray(json.data)) return [];
    return json.data;
  } catch {
    return [];
  }
}

/**
 * GET /api/screen-display-labels — throws on error (e.g. admin refresh after save).
 */
export async function fetchScreenDisplayLabels() {
  const res = await fetch(screenDisplayLabelsEndpoint(), {
    credentials: 'include',
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(apiFailureMessage(json, '화면 표시 이름을 불러오지 못했습니다.'));
    err.status = res.status;
    err.code = json?.code;
    throw err;
  }
  if (!json?.success || !Array.isArray(json.data)) {
    const err = new Error('응답 형식이 올바르지 않습니다.');
    err.status = res.status;
    throw err;
  }
  return json.data;
}

/**
 * PUT /api/screen-display-labels — system admin only.
 * @param {Array<{ screenId: string, labelUser: string, labelAdmin?: string, parentGroupId?: string|null, sortOrder?: number }>} labels
 */
export async function putScreenDisplayLabels(labels) {
  const res = await fetch(screenDisplayLabelsEndpoint(), {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ labels }),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(apiFailureMessage(json, '저장에 실패했습니다.'));
    err.status = res.status;
    err.code = json?.code;
    throw err;
  }
  return json;
}
