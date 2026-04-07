# 20260407 - LDAP directory auth via JNDI simple bind (UPN)

## 1. User requirement

### Requirement description

Integrate Active Directory (directory) authentication so that the LDAP server URL is read from **`application.yml`** / `auth.ad.ldap-url` (with existing environment override, e.g. `AUTH_AD_LDAP_URL`), and end-user authentication uses a **JNDI `InitialDirContext` simple bind** pattern instead of the current Spring **`LdapTemplate`** + manager-account pooled connection.

Bind semantics must follow the agreed reference: **`userPrincipalName = username + "@" + domain`** (or equivalent UPN suffix), with **`Context.PROVIDER_URL`** set from configured **`ldap-url`**, **`Context.SECURITY_AUTHENTICATION = "simple"`**, and credentials as **UPN + password**. Production code must use **SLF4J** for logging, must **fail closed** on misconfiguration or bind failure, must **not** use `System.out.println` or `printStackTrace`, and must **close** directory contexts in **`finally`** (or equivalent try-with-resources).

### User scenario

1. Operators deploy the backend with `auth.login.mode=ad`, set `auth.ad.ldap-url` (and env override) to the corporate LDAP/LDAPS URL.
2. Operators set a **domain / UPN suffix** configuration property (see §2) used to build the bind principal from the login `principal` field when it is not already a full UPN.
3. An end user submits AD mode login with **principal** (typically sAMAccountName or equivalent) and **password**.
4. The application authenticates by opening an LDAP context with **simple** bind using **UPN** and password; on success it continues existing **app user resolution** via `ExternalIdentityService` and provisioning rules.
5. **Problem**: The current stack uses **`LdapTemplate.authenticate`** with a **manager DN** and **user-search-base** / **user-search-filter**, which does not match the desired direct UPN bind model and ties runtime to Spring LDAP client beans for this path only.

### Expected outcome

- **`auth.ad.ldap-url`** remains the single configured **provider URL** for directory bind (with existing env override); implementers must **not** hardcode URLs.
- AD mode authentication uses **JNDI simple bind** to validate credentials (UPN + password), with **resource-safe** context lifecycle and **fail-closed** behavior consistent with existing `DIRECTORY_AUTH_FAILED` / `AUTH_CONFIGURATION_ERROR` patterns.
- **`auth.login.mode=ad`** startup validation reflects the **new** required fields (at minimum **ldap-url** and **domain / UPN suffix**); **manager DN**, **manager password**, **user-search-base**, and **user-search-filter** must be **optional** for this mode if they are no longer used for authentication (see §2 for deprecation / backward compatibility).
- **`AuthService.loginAd`** continues to call the directory authenticator and passes the **same** request **`principal`** string to **`ExternalIdentityService.findAppUserIdForDirectoryPrincipal`** after successful bind so existing mapping logic (including `@` stripping variants) remains valid.
- No new **PII** in logs; bind failures logged at **warn** (or appropriate level) **without** passwords or full UPN in clear text where avoidable (masking policy aligned with existing LDAP warning style).
- **Contract and spec** (`docs/contract.md`, `specs/external-identity-auth.spec.yaml`) are updated so required vs optional **`auth.ad.*`** keys match implementation.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable — **recommended** before implementation: directory credentials, LDAPS/TLS, log redaction)

- **Risks**: LDAP bind handles **user secrets**; misconfigured **simple bind** over plaintext LDAP exposes credentials; verbose errors could leak directory topology; **UPN** in logs may be **PII**.

- **Acceptance / recommendations**: Prefer **LDAPS or StartTLS** in production (existing spec language); ensure **no password** and minimal **user identifier** exposure in logs; keep **fail-closed** on configuration and bind failure; align with `specs/external-identity-auth.spec.yaml` security notes.

### Technical design

#### Codebase summary (Backend)

- **`AuthService.loginAd`** (`backend/src/main/java/com/logmng/service/AuthService.java`): validates request shape, IP allowlist, calls **`ldapBindAuthenticator.authenticate(principal, password)`**, then **`externalIdentityService.findAppUserIdForDirectoryPrincipal(principal)`** with the **raw** trimmed principal from the request.

- **`LdapBindAuthenticator`** (`backend/src/main/java/com/logmng/service/LdapBindAuthenticator.java`): **`@ConditionalOnProperty` `auth.login.mode=ad`**; uses **`LdapTemplate.authenticate`** with **`user-search-base`** and **`user-search-filter`**, **`principal`** as filter argument.

- **`LdapClientConfig`** (`backend/src/main/java/com/logmng/config/LdapClientConfig.java`): creates **`LdapContextSource`** with **manager** DN/password and **`ldap-url`**, exposes **`LdapTemplate`** — **only** consumed by **`LdapBindAuthenticator`** per current tree.

- **`AuthConfigurationValidator`** (`backend/src/main/java/com/logmng/config/AuthConfigurationValidator.java`): for **`ad`**, requires non-blank **`ldap-url`**, **`manager-dn`**, **`manager-password`**, **`user-search-base`**.

- **`AuthProperties.Ad`** (`backend/src/main/java/com/logmng/config/AuthProperties.java`): holds **`ldap-url`**, manager fields, search base/filter, timeouts.

- **`application.yml`**: under **`auth.ad`**, defines **`ldap-url`** and env placeholders including **`AUTH_AD_LDAP_URL`**.

- **`ExternalIdentityService.findAppUserIdForDirectoryPrincipal`**: accepts login principal and tries **full string** and **local part before `@`** for HR key lookup — compatible with **sAMAccountName** login while bind uses **UPN**.

#### Problem analysis

1. **Operational / security model mismatch**: Operations want **direct user simple bind** against **`ldap-url`**, not a **shared manager** search+bind.
2. **Unused configuration complexity**: Manager and search-base are **mandatory** today but **irrelevant** once authentication is **pure UPN bind**.
3. **Dependency surface**: **`LdapTemplate`** and **`LdapClientConfig`** exist **only** for this path; removing them reduces Spring LDAP coupling **if** no other bean references them.

#### Diagnostic phase (mandatory for error/bug fix only)

*(Not applicable — feature change.)*

#### Solution approach

**Backend:**

- Introduce a configuration property for the **UPN suffix** (DNS domain), e.g. **`auth.ad.domain`** (maps to `auth.ad.domain` / `AUTH_AD_DOMAIN` in YAML and env). **Alternative name** `auth.ad.upn-suffix` is acceptable if Contract prefers explicit UPN wording; **implementer must align** property name across `AuthProperties`, `application.yml`, validator message, and contract.

- **UPN construction**: If the login **`principal`** already contains **`@`**, use it **as the bind principal** (trimmed). Otherwise **`bindPrincipal = principal.trim() + "@" + domain.trim()`** (reference pattern). **Validate** non-blank **domain** when **`@`** is absent; **fail fast** with clear **`AUTH_CONFIGURATION_ERROR`** or equivalent if domain missing.

- **JNDI bind**: Build environment **`Hashtable`** (or `Properties`) with:

  - **`Context.INITIAL_CONTEXT_FACTORY`**: `com.sun.jndi.ldap.LdapCtxFactory` (document Sun LDAP provider assumption; note JVM requirement).

  - **`Context.PROVIDER_URL`**: from **`auth.ad.ldap-url`** (trimmed).

  - **`Context.SECURITY_AUTHENTICATION`**: **`"simple"`**.

  - **`Context.SECURITY_PRINCIPAL`**: computed UPN.

  - **`Context.SECURITY_CREDENTIALS`**: password (char[] preferred where practical; clear after use if copied).

  - Map existing **`connect-timeout-ms`** / **`read-timeout-ms`** to **`java.naming`** timeout properties where supported (verify JDK behavior; document if partial).

- **`LdapBindAuthenticator`**: Replace **`LdapTemplate`** usage with **`InitialSimpleBind`** (same class or new collaborator). Use **try-with-resources** or **`finally`** to **close** **`DirContext`**. On **`javax.naming.AuthenticationException`** (or equivalent), throw existing **`CustomException.unauthorized`** with **`DIRECTORY_AUTH_FAILED`**; on other naming errors, **fail closed** from authentication perspective (same user-facing outcome as today unless product defines differentiation). **SLF4J** only; **no** stack traces printed to stdout/stderr.

- **`LdapClientConfig`**: **Remove** or **no longer register** **`LdapTemplate`** / **`LdapContextSource`** when Spring LDAP is unused; if **`pom.xml`/`build.gradle`** drops Spring LDAP dependency, delete obsolete configuration class and clean imports.

- **`AuthConfigurationValidator`**: For **`auth.login.mode=ad`**, require **`ldap-url`** and **domain (UPN suffix)**. **Do not require** **`manager-dn`**, **`manager-password`**, **`user-search-base`** for startup when they are unused; if properties remain in **`AuthProperties`** for backward compatibility, treat as **optional** and **ignored** for authentication.

- **`AuthService`**: **No behavioral change** to mapping: after successful `authenticate`, continue **`findAppUserIdForDirectoryPrincipal(principal)`** with the **request** principal string (not only UPN), preserving current **`ExternalIdentityService`** variant logic.

**Frontend:** None (login API shape unchanged).

**DB:** None.

**Contract / spec:**

- Update **`docs/contract.md`** (auth.ad table): required keys for **`mode=ad`** must list **`ldap-url`** + **domain/UPN suffix**; reclassify manager/search fields as **optional / legacy / unused** per final product decision.

- Update **`specs/external-identity-auth.spec.yaml`** § auth.ad to match.

**Cursor tool update targets (if §1.4 applies):**

- **`.cursor/skills/auth-permission-domain/SKILL.md`** (or related auth config bullets): refresh **`auth.ad.*`** description when Step 4 completes.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | See §1.4 |

### Planned change file list (expected change targets)

#### Frontend

- *(None planned.)*

#### Backend

- `backend/src/main/java/com/logmng/config/AuthProperties.java` — Add **`domain`** (or **`upnSuffix`**) under **`Ad`**; retain legacy fields if still bound in YAML.

- `backend/src/main/resources/application.yml` — Add **`auth.ad.domain`** (or agreed name) with **`${AUTH_AD_DOMAIN:}`** (or agreed env key); document optional legacy keys.

- `backend/src/main/java/com/logmng/config/AuthConfigurationValidator.java` — **Ad** mode validation rules for **new required** set; relax manager/search **requirements**.

- `backend/src/main/java/com/logmng/service/LdapBindAuthenticator.java` — **JNDI simple bind** implementation; remove **`LdapTemplate`** dependency.

- `backend/src/main/java/com/logmng/config/LdapClientConfig.java` — **Remove or gut** if **`LdapTemplate`** no longer used.

- `backend/pom.xml` or `backend/build.gradle` — Drop **`spring-ldap`** (or equivalent) if **unreferenced** after migration.

- `backend/src/test/java/com/logmng/config/AuthConfigurationValidatorTest.java` — Extend for **ad** mode config matrix.

- `backend/src/test/java/com/logmng/service/LdapBindAuthenticatorTest.java` — **New**: UPN construction, context **close** on success/failure, exception mapping (mock **`InitialDirContext`** factory or extract testable collaborator per implementer).

#### DB

- *(None planned.)*

#### Contract / documentation (non-code product docs)

- `docs/contract.md` — **`auth.ad.*`** required vs optional.

- `specs/external-identity-auth.spec.yaml` — Same.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-LDAP-01 | Backend | Normal | `auth.login.mode=ad`, valid **`ldap-url`** + **`domain`**, no manager/search values | `AuthConfigurationValidator.validate()` **succeeds** (no `AUTH_CONFIGURATION_ERROR`) | Unit (`mvn test`) |
| TC-LDAP-02 | Backend | Exception | `mode=ad`, **blank** `ldap-url` | Startup validation **throws** `IllegalStateException` with **`AUTH_CONFIGURATION_ERROR`** | Unit |
| TC-LDAP-03 | Backend | Exception | `mode=ad`, **blank** `domain` | `AuthConfigurationValidator.validate()` **throws** `IllegalStateException` with **`AUTH_CONFIGURATION_ERROR`** | Unit |
| TC-LDAP-04 | Backend | Normal | `LdapBindAuthenticator`: principal `jdoe`, domain `corp.example.com` | Bind uses **`SECURITY_PRINCIPAL`** `jdoe@corp.example.com` | Unit (mock / spy on env builder) |
| TC-LDAP-05 | Backend | Normal | `LdapBindAuthenticator`: principal **`jdoe@corp.example.com`** | Bind uses **same** string (no double suffix) | Unit |
| TC-LDAP-06 | Backend | Normal | Successful bind | **`DirContext.close()`** invoked exactly once (try-finally / try-with-resources) | Unit (mock context) |
| TC-LDAP-07 | Backend | Exception | Bind throws **authentication** naming exception | **`CustomException`** with **`DIRECTORY_AUTH_FAILED`**; **no** `printStackTrace`; optional **warn** log | Unit |
| TC-LDAP-08 | Backend | Exception | Bind throws **non-auth** naming exception (e.g. timeout) | **Fail closed** for login path — **`DIRECTORY_AUTH_FAILED`** (or product-agreed same family) **without** leaking internals to client | Unit |
| TC-LDAP-09 | Backend | Regression | `mode=local` unchanged | Existing local validation tests still pass | Unit |
| TC-LDAP-10 | Integration | Normal | (Optional) Against test AD or containerized LDAP: real **simple** bind with UPN | Login succeeds; mapping finds **`app_user`** | Manual / integration (document command in §5 when available) |

**Note:** TC-LDAP-01 message text in the table uses intentional shorthand; implementers must match actual property names.

### Test scenarios

#### Scenario 1: Validator — AD minimal config

1. Set `AuthProperties` with `mode=ad`, `ldap-url` and `domain` only.

2. Run `AuthConfigurationValidator.validate()`.

3. **Verification**: No exception; log line may indicate validated mode.

#### Scenario 2: Authenticator — resource cleanup

1. Mock JNDI / `DirContext` to throw on bind.

2. Call `authenticate`.

3. **Verification**: Context **`close`** called; no leaked resources (optional assertion via mock).

### Test data

- Unit tests: **no DB** required for authenticator; validator uses plain **`AuthProperties`**.

- Integration: provisioned **`app_user_external_identity`** row matching login principal variant per existing **`ext_employee`** data (reference **`init-data.sql`** patterns).

### Test environment

- Frontend: N/A for this requirement.

- Backend: `http://localhost:9200` (when integration login tested).

- Database: PostgreSQL (only for optional integration login).

## 4. Checklist

### Frontend verification

- [ ] N/A

### Backend verification

- [ ] API test cases written and run (§3 automated TCs)

- [ ] Logs checked (no password / minimal PII)

- [ ] Performance checked (if applicable — new context per login; note connection cost)

### Integration

- [ ] End-to-end AD login verified when integration TC executed

- [ ] Edge cases: principal with/without `@`, blank password rejection

### Documentation

- [ ] Requirement doc completed (for workflow: §1–§3 authored; §5 after QA)

- [ ] `docs/contract.md` and spec updated with implementer

## 5. Test results

### Test run date

- *(Pending — populate after QA / implementer runs.)*

### Test results

*(Pending.)*

**Commands:**

*(Implementer: one executable command per §3 TC in §5 when recording.)*

```bash
# Example placeholder — replace after implementation
cd /Volumes/T7/dev/logmng_frontend/dev/backend && mvn test -Dtest=AuthConfigurationValidatorTest,LdapBindAuthenticatorTest
```

**Outcome:**

- *(Pending.)*

### Issues found and resolution

*(None yet.)*

### Next steps

1. Security / Contract review if not yet formalized.

2. Backend implementation per §2; update §2 change file list when done.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

*(Not applicable.)*

## 7. Final version (Korean) — add after all verification is complete

*(Deferred per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.)*

---

**Author**: Requirements (subagent)

**Date**: 2026-04-07

**Status**: In progress
