#!/usr/bin/env node
/**
 * Drift check: frontend menu/allowlists vs backend ScreenConstants + permission-groups interceptor.
 * @see docs/requirements/20260410-screen-access-menu-api-consistency.md (TC-08, §2 scripts)
 */

const fs = require('fs');
const path = require('path');

const REPO_ROOT = path.join(__dirname, '..');

function readUtf8(rel) {
  return fs.readFileSync(path.join(REPO_ROOT, rel), 'utf8');
}

function parseJavaScreenStringConstants(javaSource) {
  const map = {};
  const re = /public\s+static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"\s*;/g;
  let m;
  while ((m = re.exec(javaSource)) !== null) {
    map[m[1]] = m[2];
  }
  return map;
}

function parseAllAllowedScreens(javaSource, constMap) {
  const block = javaSource.match(/ALL_ALLOWED_SCREENS[\s\S]*?Arrays\.asList\(\s*([\s\S]*?)\)\s*\.stream/);
  if (!block) {
    throw new Error('Could not find ALL_ALLOWED_SCREENS Arrays.asList block in ScreenConstants.java');
  }
  const tokens = block[1]
    .split(/[,\s]+/)
    .map((t) => t.trim())
    .filter(Boolean);
  return tokens.map((t) => {
    if (!constMap[t]) {
      throw new Error(`Unknown token in ALL_ALLOWED_SCREENS: ${t}`);
    }
    return constMap[t];
  });
}

function parseJsExportedStringArray(source, exportName) {
  const re = new RegExp(`export const ${exportName} = \\[([\\s\\S]*?)\\];`);
  const block = source.match(re);
  if (!block) {
    throw new Error(`Could not find export const ${exportName} in menuTree.js`);
  }
  const inner = block[1];
  const out = [];
  const strRe = /'([^']*)'/g;
  let m;
  while ((m = strRe.exec(inner)) !== null) {
    out.push(m[1]);
  }
  return out;
}

function parseMenuTreeViewIds(menuTreeSource) {
  const ids = new Set();
  const strRe = /view:\s*'([^']+)'/g;
  let m;
  while ((m = strRe.exec(menuTreeSource)) !== null) {
    ids.add(m[1]);
  }
  return [...ids];
}

function parseExpectedPermissionGroupsApiIds(policySource) {
  const block = policySource.match(
    /export const PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS = \[([\s\S]*?)\];/
  );
  if (!block) {
    throw new Error('PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS not found in screenAccessPolicy.js');
  }
  const out = [];
  const strRe = /'([^']*)'/g;
  let m;
  while ((m = strRe.exec(block[1])) !== null) {
    out.push(m[1]);
  }
  return out;
}

function parseInterceptorPathRules(interceptorSource, constMap) {
  const rules = [];
  const ruleRe = /new PathScreenRule\(\s*"([^"]+)"\s*,\s*List\.of\(([^)]*)\)\s*\)/g;
  let m;
  while ((m = ruleRe.exec(interceptorSource)) !== null) {
    const pattern = m[1];
    const listBody = m[2];
    const screenIds = listBody
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .map((ref) => {
        const r = ref.replace(/^ScreenConstants\./, '');
        if (!constMap[r]) {
          throw new Error(`Unknown ScreenConstants ref in interceptor: ${ref}`);
        }
        return constMap[r];
      });
    rules.push({ pattern, screenIds });
  }
  return rules;
}

function setDiff(a, b) {
  const sa = new Set(a);
  const sb = new Set(b);
  const onlyA = [...sa].filter((x) => !sb.has(x));
  const onlyB = [...sb].filter((x) => !sa.has(x));
  return { onlyA, onlyB };
}

/**
 * TC-08: MENU_TREE views ⊆ ALLOWED_SCREEN_IDS ⊆ backend allowlist parity; ORDERED_SCREEN_IDS contains each MENU_TREE view.
 * @returns {{ errors: string[] }}
 */
function verifyMenuAndAllowlists() {
  const errors = [];

  const screenConstantsSrc = readUtf8('backend/src/main/java/com/logmng/constants/ScreenConstants.java');
  const menuTreeSrc = readUtf8('frontend/src/constants/menuTree.js');

  const javaMap = parseJavaScreenStringConstants(screenConstantsSrc);
  const backendAllowed = parseAllAllowedScreens(screenConstantsSrc, javaMap);

  const frontendAllowed = parseJsExportedStringArray(menuTreeSrc, 'ALLOWED_SCREEN_IDS');
  const ordered = parseJsExportedStringArray(menuTreeSrc, 'ORDERED_SCREEN_IDS');
  const menuViews = parseMenuTreeViewIds(menuTreeSrc);

  const feSet = new Set(frontendAllowed);
  const { onlyA: missingInFrontend, onlyB: extraInFrontend } = setDiff(backendAllowed, frontendAllowed);
  if (missingInFrontend.length || extraInFrontend.length) {
    errors.push(
      `ALLOWED_SCREEN_IDS must match ScreenConstants ALL_ALLOWED_SCREENS (as sets). ` +
        `Only in backend: ${missingInFrontend.join(', ') || '(none)'}; ` +
        `Only in frontend: ${extraInFrontend.join(', ') || '(none)'}`
    );
  }

  for (const v of menuViews) {
    if (!feSet.has(v)) {
      errors.push(`MENU_TREE view "${v}" is missing from ALLOWED_SCREEN_IDS`);
    }
    if (!ordered.includes(v)) {
      errors.push(`MENU_TREE view "${v}" is missing from ORDERED_SCREEN_IDS`);
    }
  }

  return { errors };
}

/**
 * Policy module expectation vs ScreenAccessInterceptor `/api/permission-groups.*` (requires Backend alignment).
 * @returns {{ errors: string[] }}
 */
function verifyPermissionGroupInterceptorPolicy() {
  const errors = [];

  const screenConstantsSrc = readUtf8('backend/src/main/java/com/logmng/constants/ScreenConstants.java');
  const interceptorSrc = readUtf8('backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java');
  const policySrc = readUtf8('frontend/src/constants/screenAccessPolicy.js');

  const javaMap = parseJavaScreenStringConstants(screenConstantsSrc);
  const expectedPg = parseExpectedPermissionGroupsApiIds(policySrc);
  const rules = parseInterceptorPathRules(interceptorSrc, javaMap);
  const pgRule = rules.find((r) => r.pattern === '^/api/permission-groups.*');
  if (!pgRule) {
    errors.push('No PathScreenRule for ^/api/permission-groups.* in ScreenAccessInterceptor.java');
    return { errors };
  }
  const actualSet = new Set(pgRule.screenIds);
  const missing = expectedPg.filter((id) => !actualSet.has(id));
  if (missing.length) {
    errors.push(
      `Interceptor /api/permission-groups.* must list every PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS entry. Missing: ${missing.join(
        ', '
      )}`
    );
  }
  return { errors };
}

function runAllChecks() {
  const a = verifyMenuAndAllowlists();
  const b = verifyPermissionGroupInterceptorPolicy();
  return [...a.errors, ...b.errors];
}

function main() {
  let errors;
  try {
    errors = runAllChecks();
  } catch (e) {
    console.error('verify-screen-access-consistency: FAILED (parse/runtime error)');
    console.error(e.message || e);
    process.exit(1);
    return;
  }

  if (errors.length) {
    console.error('verify-screen-access-consistency: FAILED\n');
    for (const e of errors) {
      console.error(`- ${e}`);
    }
    process.exit(1);
  }

  console.log('verify-screen-access-consistency: OK');
  console.log('- ALLOWED_SCREEN_IDS ↔ ScreenConstants: match');
  console.log('- MENU_TREE views covered in ORDERED_SCREEN_IDS');
  console.log('- Permission-groups interceptor ⊇ PERMISSION_GROUPS_API_EXPECTED_SCREEN_IDS');
  process.exit(0);
}

if (require.main === module) {
  main();
}

module.exports = {
  verifyMenuAndAllowlists,
  verifyPermissionGroupInterceptorPolicy,
  runAllChecks,
};
