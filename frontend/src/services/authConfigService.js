/**
 * Public auth mode for login form shape (local vs AD). Server source of truth is auth.login.mode;
 * when GET /api/auth/config is not available, use REACT_APP_AUTH_LOGIN_MODE (default local).
 */
import { getApiBaseUrl } from '../config/runtimeApi';

/**
 * @returns {Promise<'local'|'ad'>}
 */
export async function fetchAuthLoginMode() {
  const apiBaseUrl = getApiBaseUrl();
  try {
    const response = await fetch(`${apiBaseUrl}/auth/config`, {
      credentials: 'include',
    });
    if (!response.ok) {
      throw new Error(`auth config HTTP ${response.status}`);
    }
    const result = await response.json();
    const mode = result?.data?.loginMode ?? result?.data?.authLoginMode;
    if (mode === 'ad' || mode === 'local') {
      return mode;
    }
  } catch {
    // Contract gap: public config endpoint may be absent — fall back to build-time env.
  }
  const env = process.env.REACT_APP_AUTH_LOGIN_MODE;
  if (env === 'ad' || env === 'local') {
    return env;
  }
  return 'local';
}
