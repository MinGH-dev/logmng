/**
 * Tab-scoped last main-menu screen id (sessionStorage).
 * @see docs/requirements/20260420-preserve-view-on-refresh.md
 */

export const LOGMNG_LAST_VIEW_SESSION_KEY = 'logmng_last_view';

export function getLastViewId() {
  try {
    if (typeof sessionStorage === 'undefined') return null;
    const v = sessionStorage.getItem(LOGMNG_LAST_VIEW_SESSION_KEY);
    return v != null && v !== '' ? v : null;
  } catch {
    return null;
  }
}

export function setLastViewId(viewId) {
  try {
    if (typeof sessionStorage === 'undefined' || viewId == null || viewId === '') return;
    sessionStorage.setItem(LOGMNG_LAST_VIEW_SESSION_KEY, String(viewId));
  } catch {
    /* ignore */
  }
}

export function clearLastViewStorage() {
  try {
    if (typeof sessionStorage === 'undefined') return;
    sessionStorage.removeItem(LOGMNG_LAST_VIEW_SESSION_KEY);
  } catch {
    /* ignore */
  }
}
