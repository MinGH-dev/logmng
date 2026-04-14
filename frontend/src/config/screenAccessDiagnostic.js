/**
 * Opt-in screen-access diagnostics (req 20260414). Off by default; never enable in production bundles for verbose dumps.
 * Set REACT_APP_SCREEN_ACCESS_DIAGNOSTIC=1 in .env.development.local to log session vs 관리 메뉴 resolution.
 */
export function isScreenAccessDiagnosticEnabled() {
  if (typeof process === 'undefined') return false;
  if (process.env.NODE_ENV === 'production') return false;
  return process.env.REACT_APP_SCREEN_ACCESS_DIAGNOSTIC === '1';
}
