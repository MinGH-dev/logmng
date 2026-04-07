/**
 * Admin provisioning APIs — docs/api-definition.md §2a, specs/external-identity-auth.spec.yaml §4.
 */
import { getApiBaseUrl } from '../config/runtimeApi';

const json = async (response) => {
  const result = await response.json();
  if (!response.ok) {
    const msg = result.error || `HTTP ${response.status}`;
    const err = new Error(msg);
    err.status = response.status;
    err.code = result.code;
    /** Full error JSON for UI (e.g. 409 existingUsername / existingAppUserId). */
    err.payload = result;
    throw err;
  }
  return result;
};

/**
 * @param {object} body
 * @param {string} [body.departmentName] — filter by department name
 * @param {string} [body.keyword] — employee display name search
 * @param {string} [body.employeeNumber]
 * @param {string} [body.sourceSystem]
 * @param {number} [body.page]
 * @param {number} [body.pageSize]
 */
export async function searchExternalEmployees(body) {
  const apiBaseUrl = getApiBaseUrl();
  const response = await fetch(`${apiBaseUrl}/provisioning/external-employees/search`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return json(response);
}

export async function searchExternalDepartments(body) {
  const apiBaseUrl = getApiBaseUrl();
  const response = await fetch(`${apiBaseUrl}/provisioning/external-departments/search`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return json(response);
}

export async function provisionUserFromExternalEmployee(body) {
  const apiBaseUrl = getApiBaseUrl();
  const response = await fetch(`${apiBaseUrl}/provisioning/users/from-external-employee`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return json(response);
}
