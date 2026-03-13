# DB Definition (for design reference)

Summary of database tables and column types/sizes. Use when defining **input maxLength**, **select option length**, or **display width** so that UI aligns with persisted data. Source: `backend/src/main/resources/db/schema.sql`, `schema_imagelog.sql`, `schema_user_activity_log.sql`.

---

## 1. Log tables (검색하기 / main)

### 1.1 pb_send, pb_recv (logType: pb_feplog)

| Column        | Type         | Size / Notes        | UI implication |
|---------------|--------------|----------------------|----------------|
| id            | BIGSERIAL    | —                    | Display only    |
| log_timestamp | TIMESTAMP    | —                    | Date/time       |
| media_code    | VARCHAR(10)  | max 10 chars         | Input maxLength ≤ 10 |
| tr_code       | VARCHAR(20)  | max 20 chars         | Input maxLength ≤ 20 |
| user_id       | VARCHAR(50)  | max 50 chars         | Input maxLength ≤ 50 |
| ip_address   | VARCHAR(45)  | IPv6                 | Input maxLength ≤ 45 |
| session_id    | VARCHAR(100) | max 100              | —               |
| device_type   | VARCHAR(20)  | max 20               | —               |
| request_data, response_data, error_message, user_agent | TEXT | unbounded | Large text / no strict width |

### 1.2 imagelog (logType: java_fw_imglog)

| Column       | Type        | Size / Notes   | UI implication |
|--------------|-------------|----------------|----------------|
| application  | VARCHAR(256) | max 256 chars | Input maxLength ≤ 256 |
| servicegroup  | VARCHAR(256) | max 256 chars | Input maxLength ≤ 256 |
| service       | VARCHAR(256) | max 256 chars | Input maxLength ≤ 256 |
| status        | VARCHAR(256) | max 256 chars | —              |
| datastring    | TEXT         | unbounded     | Search field   |
| guid          | VARCHAR(256) | max 256 chars | Display/link   |
| headerstring  | TEXT         | unbounded     | Search field   |
| insert_time   | BIGINT       | —             | Date/time      |

---

## 2. Search history & approval

### 2.1 search_history

| Column           | Type         | Size / Notes   | UI implication |
|------------------|--------------|----------------|----------------|
| id               | BIGSERIAL    | —              | ID display     |
| user_id           | VARCHAR(100) | max 100 chars | Requester ID   |
| log_type         | VARCHAR(50)  | max 50 chars  | Log type select |
| search_params    | TEXT         | JSON           | Summary only in list |
| requested_at     | TIMESTAMP    | —              | Date/time      |
| expires_at       | TIMESTAMP    | —              | Date/time      |
| approval_status  | VARCHAR(20)  | PENDING, APPROVED, EXPIRED, REJECTED | Status badge |
| approved_by      | VARCHAR(100) | nullable      | Display        |
| rejected_by      | VARCHAR(100) | nullable      | Display        |
| rejection_reason | TEXT         | nullable      | Modal input    |

### 2.2 search_history_approved_row

| Column            | Type         | Size / Notes | UI implication |
|-------------------|--------------|--------------|----------------|
| search_history_id | BIGINT       | FK           | —              |
| log_type         | VARCHAR(50)  | —           | —              |
| row_id           | VARCHAR(512)  | max 512     | Decryption scope |

---

## 3. User & organization

### 3.1 department

| Column      | Type         | Size / Notes | UI implication |
|-------------|--------------|--------------|----------------|
| code        | VARCHAR(50)  | PK, max 50  | Select value   |
| parent_code | VARCHAR(50)  | nullable    | Tree           |
| name        | VARCHAR(200)  | max 200     | Select label, display |
| sort_order  | INT          | —           | Ordering       |

### 3.2 app_user

| Column          | Type         | Size / Notes | UI implication |
|-----------------|--------------|--------------|----------------|
| username        | VARCHAR(100)  | UNIQUE, max 100 | UserId, login |
| department_code | VARCHAR(50)   | nullable, FK  | Department select value |
| position        | VARCHAR(50)   | nullable     | Display        |
| rank            | VARCHAR(50)   | nullable     | Display        |
| role            | VARCHAR(20)   | ADMIN, USER   | —              |

### 3.3 decrypt_approver

| Column          | Type         | Size / Notes | UI implication |
|-----------------|--------------|--------------|----------------|
| user_id         | VARCHAR(100)  | app_user.username | Approver select |
| department_code | VARCHAR(50)   | NULL = global | Department scope |

---

## 4. Permission groups

### 4.1 permission_group

| Column       | Type        | Size / Notes | UI implication |
|--------------|-------------|--------------|----------------|
| code         | VARCHAR(50) | UNIQUE      | Code input     |
| name         | VARCHAR(200) | max 200    | Name input     |
| description  | TEXT        | nullable     | Text area      |
| sort_order   | INT         | —            | —              |

### 4.2 permission_group_screen

| Column             | Type         | Size / Notes | UI implication |
|--------------------|--------------|--------------|----------------|
| screen_id          | VARCHAR(50)  | main, search-history, activity-log, statistics, pending-approvals, user-management, department-approvers, user-permission-hierarchy, permission-group-management | Fixed set for selects |
| scope              | VARCHAR(10)  | self, team, all (nullable → team) | Scope dropdown |
| read, write, approve, decrypt | BOOLEAN | NULL = derived | Checkboxes     |

---

## 5. Activity log (활동 이력)

### 5.1 user_activity_log

| Column          | Type         | Size / Notes | UI implication |
|-----------------|--------------|--------------|----------------|
| user_id         | VARCHAR(100) | max 100     | User filter    |
| username        | VARCHAR(100) | nullable    | Display, filter (name) |
| action_type     | VARCHAR(50)  | LOGIN, LOGOUT, SEARCH, VIEW, EXPORT, etc. | Action filter select |
| action_detail   | TEXT         | JSON         | Detail view    |
| ip_address      | VARCHAR(45)  | IPv6         | Filter maxLength ≤ 45 |
| request_path    | VARCHAR(500) | max 500     | Display        |
| response_status | INTEGER      | —            | Display        |

---

## 6. Suggested UI constraints (from sizes above)

- **Short codes (media, tr_code, device_type)**: maxLength 10–20.
- **User/login IDs**: maxLength 50–100.
- **Department code**: maxLength 50; name display up to 200.
- **IP address**: maxLength 45.
- **imagelog application/servicegroup/service**: up to 256; for compact search forms, consider min/max width that fits ~20–30 chars visible, full value in tooltip or detail.
- **Status/category (approval_status, action_type)**: fixed enum; select or badge, no free text.
- **screen_id**: fixed set; use allowed list from permission spec, not free text.

When defining **design standards**, prefer these limits for input fields and labels so that data from API/DB is never truncated or misaligned on screen.
