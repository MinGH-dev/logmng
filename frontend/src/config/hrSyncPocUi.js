/**
 * HR Sync PoC: optional sidebar entry; deep link may still be used when the user has access.
 * REACT_APP_HR_SYNC_POC_UI=true — show 관리 메뉴 항목.
 */
export function isHrSyncPocMenuEnabled() {
  return process.env.REACT_APP_HR_SYNC_POC_UI === 'true';
}

/** GET .../employees — spec default / max (specs/hr-sync-poc.spec.yaml §4.4) */
export const DEFAULT_EMPLOYEES_PAGE_SIZE = 20;
export const MIN_EMPLOYEES_PAGE_SIZE = 1;
export const MAX_EMPLOYEES_PAGE_SIZE = 100;
export const EMPLOYEES_PAGE_SIZE_OPTIONS = [20, 50, 100];
