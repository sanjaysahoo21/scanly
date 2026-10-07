# Database Push Integration — Implementation Log

> **Feature**: Push extracted invoice data directly into a user's own database (PostgreSQL / MySQL)  
> **Reference design doc**: [`database-push-integration.md`](./database-push-integration.md)  
> **Implementation date**: October 2026  
> **Total parts**: 5 (3 complete, 2 remaining)

---

## Table of Contents

1. [Part 1 — Backend Foundation](#part-1--backend-foundation)
2. [Part 2 — Backend Push Logic](#part-2--backend-push-logic)
3. [Part 3 — Frontend Integrations Page](#part-3--frontend-integrations-page)
4. [Part 4 — Push from Invoices Page](#part-4--push-from-invoices-page-remaining) *(remaining)*
5. [Part 5 — Tier 2 Field Mapper](#part-5--tier-2-field-mapper-remaining) *(remaining)*
6. [Bugs Encountered & Fixes](#bugs-encountered--fixes)
7. [Testing Log](#testing-log)
8. [Architecture Reference](#architecture-reference)

---

## Part 1 — Backend Foundation

**Status**: ✅ Complete  
**Goal**: Create the database entities, encryption service, and all CRUD + test-connection endpoints. No push logic yet — just the foundation needed to save and test a connection config.

### Files Created

| File | Purpose |
|------|---------|
| `backend/.../entity/IntegrationConfig.java` | JPA entity — stores one DB connection config per user/org. Passwords stored encrypted (never plaintext). |
| `backend/.../entity/IntegrationPushLog.java` | JPA entity — records the result of every push (counts + per-invoice failure JSON). |
| `backend/.../repository/IntegrationConfigRepository.java` | Spring Data repository — org-scoped queries. |
| `backend/.../repository/IntegrationPushLogRepository.java` | Spring Data repository — ordered push history per integration. |
| `backend/.../service/CryptoService.java` | AES-256-GCM encryption/decryption for database passwords. |
| `backend/.../service/IntegrationService.java` | Service — CRUD + `testConnection()` with human-readable error classification. |
| `backend/.../dto/IntegrationRequest.java` | Validated request DTO — all fields validated with JSR-380 annotations. |
| `backend/.../dto/IntegrationResponse.java` | Safe response DTO — intentionally omits `encryptedPassword`. |
| `backend/.../controller/IntegrationController.java` | REST controller — 6 endpoints (list, create, update, delete, test-before-save, test-saved). |

### application.properties addition

```properties
# AES-256-GCM key for encrypting integration passwords
# Override in production via env var SCANLY_CRYPTO_SECRET
scanly.crypto.secret=${SCANLY_CRYPTO_SECRET:747dWtoQNh5Mvc96yYkR4d17T1sRCTnfY1JX/HoYDlQ=}
```

> ⚠️ **Production note**: Generate a fresh key per environment. Never use the default key in production.  
> Generate: `$rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new(); $b = New-Object byte[] 32; $rng.GetBytes($b); [Convert]::ToBase64String($b)`

### API Endpoints (Part 1)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/integrations` | JWT | List all integrations for org |
| `POST` | `/api/v1/integrations` | JWT | Create new integration (encrypts password) |
| `PUT` | `/api/v1/integrations/{id}` | JWT | Update integration (password optional) |
| `DELETE` | `/api/v1/integrations/{id}` | JWT | Delete integration |
| `POST` | `/api/v1/integrations/test-connection` | JWT | Test connection with raw credentials (pre-save) |
| `POST` | `/api/v1/integrations/{id}/test` | JWT | Test connection for a saved integration |

### CryptoService — How it works

```
Encryption:
  1. Generate random 12-byte IV (GCM nonce)
  2. AES-256-GCM encrypt plaintext
  3. Concatenate: IV (12 bytes) + ciphertext+tag
  4. Base64-encode → stored in DB

Decryption:
  1. Base64-decode
  2. Split: first 12 bytes = IV, rest = ciphertext+tag
  3. AES-256-GCM decrypt → plaintext password
```

### Connection Test Error Classification

The `testConnection()` method classifies raw JDBC exceptions into user-friendly messages:

| JDBC Error | User Message |
|-----------|-------------|
| `Connection refused` | "Cannot reach the database server at host:port..." |
| `Timeout / timed out` | "Connection timed out after 6 seconds..." |
| `28P01` / `28000` (auth) | "Authentication failed. Check your username and password." |
| SSL required | "This database requires SSL. Enable the SSL toggle and try again." |
| Database does not exist | "Database not found. Check the database name field." |
| Unknown host | "Hostname 'x' could not be resolved. Check the host field." |
| Other | Raw exception message as fallback |

### Test Result (Confirmed Working)

```powershell
POST /api/v1/integrations/test-connection
# Response:
{
  "success": true,
  "message": "Connection successful",
  "latencyMs": 46
}
```

---

## Part 2 — Backend Push Logic

**Status**: ✅ Complete  
**Goal**: Add the actual push mechanism — auto-create the target tables (Tier 1), INSERT invoice data, handle errors per-invoice, and log the result.

### Files Created

| File | Purpose |
|------|---------|
| `backend/.../service/DDLGenerator.java` | Generates `CREATE TABLE IF NOT EXISTS` DDL for PostgreSQL and MySQL. Idempotent — safe to run multiple times. |

### Files Modified

| File | What changed |
|------|-------------|
| `backend/.../service/IntegrationService.java` | Added `pushInvoices()`, `runDDL()`, `insertInvoiceHeader()`, `insertLineItems()`, `savePushLog()`, `getLogsForIntegration()` |
| `backend/.../repository/InvoiceRepository.java` | Added `findAllByDocumentOrganizationId()` for push-all support |
| `backend/.../controller/IntegrationController.java` | Added `POST /{id}/push` and `GET /{id}/logs` endpoints |

### Auto-Created Tables (Tier 1)

When `targetTable` is null/blank, the service runs DDL to create:

```sql
-- PostgreSQL
CREATE TABLE IF NOT EXISTS scanly_invoices (
    id                UUID         PRIMARY KEY,
    invoice_number    VARCHAR(100),
    vendor_name       VARCHAR(500),
    ...
    pushed_at         TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scanly_line_items (
    id            UUID        PRIMARY KEY,
    invoice_id    UUID        NOT NULL REFERENCES scanly_invoices(id) ON DELETE CASCADE,
    ...
);
```

### Push Flow

```
pushInvoices(cfg, invoiceIds, pushAll)
    │
    ├── Decrypt password (CryptoService)
    ├── Load invoices (InvoiceRepository)
    ├── Open JDBC connection (6s timeout)
    │
    ├── [Tier 1] runDDL() → CREATE TABLE IF NOT EXISTS
    │
    └── For each invoice:
            insertInvoiceHeader()  → ON CONFLICT (id) DO NOTHING
            insertLineItems()      → batch INSERT
            ✅ succeeded++ or ❌ collect PushFailure
    │
    └── savePushLog() → persist IntegrationPushLog
    └── return PushResult { status, counts, failures }
```

### Push Modes

| Mode | Behaviour |
|------|----------|
| `best_effort` | Each INSERT is auto-committed individually. Failures are collected but others proceed. |
| `all_or_nothing` | *(planned in Tier 2)* — single transaction, any failure rolls back all |

### New API Endpoints (Part 2)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/integrations/{id}/push` | `{"pushAll":true}` or `{"invoiceIds":["uuid1",...]}` | Push invoices to target DB |
| `GET` | `/api/v1/integrations/{id}/logs` | — | Get push history (most recent first) |

### Push Response Format

```json
{
  "status": "SUCCESS",
  "totalAttempted": 37,
  "totalSucceeded": 37,
  "totalFailed": 0,
  "failures": []
}
```

**Partial failure example:**
```json
{
  "status": "PARTIAL",
  "totalAttempted": 37,
  "totalSucceeded": 34,
  "totalFailed": 3,
  "failures": [
    { "invoiceId": "uuid-x", "invoiceNumber": "INV-042", "error": "Value too long for a column." },
    { "invoiceId": "uuid-y", "invoiceNumber": "INV-087", "error": "Null value violates NOT NULL constraint." }
  ]
}
```

### Test Result (Confirmed Working)

```powershell
POST /api/v1/integrations/{id}/push  Body: {"pushAll":true}
# Response:
{
  "status": "SUCCESS",
  "totalSucceeded": 37,
  "totalFailed": 0,
  "totalAttempted": 37,
  "failures": []
}

# PostgreSQL verification:
SELECT COUNT(*) FROM scanly_invoices;
-- total_pushed: 37
```

---

## Part 3 — Frontend Integrations Page

**Status**: ✅ Complete  
**Goal**: Build a full Settings → Integrations UI that allows users to manage integrations, test connections, push invoices, and view push history — without using the API directly.

### Files Created

| File | Purpose |
|------|---------|
| `frontend/src/pages/IntegrationsPage.jsx` | Full integrations management page |
| `frontend/src/services/integrationService.js` | API service — wraps all 8 integration endpoints |
| `frontend/src/styles/integrations.css` | Dedicated CSS for wizard, cards, push result panel |

### Files Modified

| File | What changed |
|------|-------------|
| `frontend/src/App.jsx` | Added route `/settings/integrations → IntegrationsPage` |
| `frontend/src/components/layout/Sidebar.jsx` | Added "Integrations" nav item (Database icon) |

### UI Components in IntegrationsPage

#### 1. Integration Card
Each saved integration renders as a card showing:
- DB type emoji (🐘 PostgreSQL, 🐬 MySQL) + label
- `host:port/database` connection string
- Badges: DB type, tier (auto-create vs existing table), SSL status
- Added timestamp
- **3 action icon buttons**: Test connection 📶 | Push all ▶ | Delete 🗑
- Connection test result inline (green = OK, red = error message)
- Push result panel (success/partial/failed with per-invoice failure list)
- Collapsible push history logs

#### 2. Add Integration Wizard (Modal)
A full-screen modal with:
- **Label** field
- **DB type selector** — visual buttons for PostgreSQL/MySQL
- **Connection fields** — host, port, database, username, password (auto-port when type changes)
- **Options** — SSL toggle, line items strategy dropdown, push mode dropdown
- **Target table** — optional, blank = Tier 1 auto-create
- **Test Connection** button — live feedback before saving
- **Save Integration** button

#### 3. Push Result Panel
Shown after every push:
- Status chip (SUCCESS / PARTIAL / FAILED)
- Count stats (e.g. "✅ 35 succeeded  ❌ 2 failed")
- Per-invoice failure list (up to 5 shown, "N more" if beyond)
- Close (×) button

#### 4. Empty State
When no integrations exist:
- Large Database icon
- Descriptive text
- "Add your first integration" primary button

### Route & Navigation

```
Sidebar → Integrations (Database icon)
  → /settings/integrations
    → IntegrationsPage
```

---

## Part 4 — Push from Invoices Page *(Remaining)*

**Status**: ⏳ Not started  
**Goal**: Add a "Push to Database" action directly on the Invoices page, so users can push the current view (all or selected invoices) to any of their configured integrations without navigating to Settings.

### What will be built

**Frontend only** — no new backend changes needed.

#### UI additions to `InvoicesPage.jsx`

1. **Push dropdown button** in the header action bar (next to Export):
   - "Push all invoices" → pushes everything
   - "Push selected (N)" → only pushes the selected invoice IDs
   - Each option shows a sub-list of configured integrations to pick from

2. **Integration picker popover**:
   - Lists all saved integrations by label
   - One click → triggers push to that integration
   - Shows loading spinner + result inline

3. **Result toast/panel** — same PushResultPanel component reused from IntegrationsPage.

#### Component plan

```jsx
// New component
PushToDbButton.jsx
  props: { selectedIds: Set<string>, onPushComplete: fn }
  - loads integrations on mount
  - renders dropdown with integrations
  - calls pushInvoices(integrationId, { invoiceIds: [...selectedIds] })
  - shows result panel
```

### No backend changes needed

The existing `POST /api/v1/integrations/{id}/push` with `{"invoiceIds":["uuid1","uuid2"]}` already supports selective push.

---

## Part 5 — Tier 2 Field Mapper *(Remaining)*

**Status**: ⏳ Not started  
**Goal**: Allow users to push into their **existing** table with custom column mapping. This requires schema introspection + a visual field mapper UI + dynamic INSERT with type coercion.

### What will be built

#### Backend

**New endpoint:**

```
GET /api/v1/integrations/{id}/schema?table={tableName}
```

Response — fetches column metadata from `INFORMATION_SCHEMA`:
```json
{
  "tableName": "bills",
  "columns": [
    { "columnName": "bill_id",    "dataType": "character varying", "maxLength": 50,  "nullable": false },
    { "columnName": "party_name", "dataType": "character varying", "maxLength": 200, "nullable": true },
    { "columnName": "amount",     "dataType": "numeric",           "precision": 15,  "nullable": false }
  ]
}
```

**New service logic in `IntegrationService`:**
- `introspectSchema(cfg, tableName)` → runs `INFORMATION_SCHEMA` query
- `insertTier2(conn, inv, cfg)` → dynamic INSERT built from saved `fieldMapping` JSON + type coercion

**Type coercion layer:**
```java
class TypeCoercion {
  static Object coerce(Object value, String targetSqlType) {
    // BigDecimal → VARCHAR = .toString()
    // LocalDate  → BIGINT  = .toEpochDay()
    // Boolean    → CHAR(1) = "Y"/"N"
    // UUID       → VARCHAR = .toString()
    // etc.
  }
}
```

#### Frontend

**Step added to `AddIntegrationWizard`:**

When user enters a `targetTable` name and clicks "Load columns", the wizard calls the schema endpoint and shows the field mapper:

```
Map Fields — bills

Our Data Field          →    Your Column            Type
────────────────────────────────────────────────────────
Invoice Number          →   [ bill_id      ▼ ]   VARCHAR(50)
Vendor Name             →   [ party_name   ▼ ]   VARCHAR(200)
Total Amount            →   [ amount       ▼ ]   NUMERIC(15,2)
Invoice Date            →   [ bill_date    ▼ ]   DATE
Vendor GSTIN            →   [ gst_no       ▼ ]   VARCHAR(20)
Line Items              →   [ Strategy: Skip ▼ ]

[Save Mapping]
```

**Mapping stored as JSON in `IntegrationConfig.fieldMapping`:**
```json
{
  "invoiceNumber": "bill_id",
  "vendorName": "party_name",
  "totalAmount": "amount",
  "invoiceDate": "bill_date"
}
```

**Push flow for Tier 2:**
1. Load saved `fieldMapping`
2. Re-introspect `INFORMATION_SCHEMA` to validate mapping is still valid
3. For each invoice: extract mapped field values → coerce types → dynamic INSERT

---

## Bugs Encountered & Fixes

### Bug 1 — DDL comment-skip skipped entire first statement

**Symptom:**
```
Connection failed: ERROR: relation "scanly_invoices" does not exist
```

**Root cause:**  
The `POSTGRESQL_DDL` text block starts with a `-- Auto-generated...` comment on the first line. The `runDDL()` method split on `;`, which grouped the comment + `CREATE TABLE scanly_invoices` into one string. The old check `if (!trimmed.startsWith("--"))` saw the string started with `--` and **skipped the entire first statement**, including the `CREATE TABLE`.

**Fix:**  
Strip comment lines *before* splitting on semicolons:
```java
String cleaned = Arrays.stream(ddl.split("\n"))
    .filter(line -> !line.strip().startsWith("--"))
    .collect(Collectors.joining("\n"));

for (String stmt : cleaned.split(";")) { ... }
```

---

### Bug 2 — UUID type mismatch in PostgreSQL INSERT

**Symptom:**
```
ERROR: column "id" is of type uuid but expression is of type character varying
Hint: You will need to rewrite or cast the expression.
```

**Root cause:**  
`ps.setString(1, invoice.getId().toString())` sends a `VARCHAR` to a `UUID` column. PostgreSQL JDBC is strict — it does not auto-cast `character varying` → `uuid`.

**Fix:**  
Use `ps.setObject(1, invoice.getId())` which sends the Java `UUID` object directly. The PostgreSQL JDBC driver correctly maps `java.util.UUID` → PostgreSQL `uuid` type.

```java
// Before (wrong)
ps.setString(1, inv.getId().toString());

// After (correct)
ps.setObject(1, inv.getId());  // sends UUID type directly
```

Same fix applied to line item IDs and `invoice_id` FK.

---

### Bug 3 — Frontend import error: `apiService.js` not found

**Symptom:**
```
[plugin:vite:import-analysis] Failed to resolve import "./apiService.js"
from "src/services/integrationService.js"
```

**Root cause:**  
`integrationService.js` was written using a non-existent shared `apiRequest` helper from `./apiService.js`. The project does not have a shared API utility — each service has its own inline `request()` function using `getToken()` from `authService.js`.

**Fix:**  
Rewrote `integrationService.js` to follow the existing project pattern:
```js
import { getToken } from './authService.js'

async function request(path = '', options = {}) {
  const res = await fetch(`/api/v1/integrations${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      ...(options.headers || {}),
    },
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(data.message || `Request failed (${res.status})`)
  return data
}
```

---

## Testing Log

| Test | Method | Result |
|------|--------|--------|
| Test connection (valid credentials) | `POST /integrations/test-connection` | ✅ `{ success: true, latencyMs: 46 }` |
| Test connection (wrong password) | `POST /integrations/test-connection` | ✅ `{ success: false, message: "Authentication failed..." }` |
| Save integration | `POST /integrations` | ✅ Returns `IntegrationResponse` without password |
| List integrations | `GET /integrations` | ✅ Returns org-scoped list |
| Push all invoices — first attempt | `POST /integrations/{id}/push` | ❌ DDL failed (Bug 1) |
| Push all invoices — after Bug 1 fix | `POST /integrations/{id}/push` | ❌ UUID type error (Bug 2) |
| Push all invoices — after Bug 2 fix | `POST /integrations/{id}/push` | ✅ `status: SUCCESS, totalSucceeded: 37` |
| Verify in DB | `psql -c "SELECT COUNT(*) FROM scanly_invoices"` | ✅ `total_pushed: 37` |
| Push logs | `GET /integrations/{id}/logs` | ✅ Returns 3 log entries (2 FAILED, 1 SUCCESS) |

---

## Architecture Reference

```
┌──────────────────────────────────────────────────────────────────┐
│                        Frontend (React)                           │
│                                                                   │
│   Sidebar → /settings/integrations                               │
│     IntegrationsPage                                             │
│       ├── AddIntegrationWizard (modal)                          │
│       │     ├── DB type selector                                │
│       │     ├── Connection fields + Test Connection btn          │
│       │     └── Save → POST /integrations                       │
│       │                                                         │
│       └── IntegrationCard (per saved integration)               │
│             ├── Test btn → POST /integrations/{id}/test         │
│             ├── Push btn → POST /integrations/{id}/push         │
│             ├── PushResultPanel (inline)                        │
│             └── Logs → GET /integrations/{id}/logs              │
│                                                                  │
│   integrationService.js → wraps all endpoints with getToken()   │
└──────────────────────────────────────────────────────────────────┘
                              │ JWT Bearer
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                          │
│                                                                   │
│   IntegrationController   →   IntegrationService                │
│                                 ├── CryptoService (AES-256-GCM) │
│                                 ├── DDLGenerator                 │
│                                 └── InvoiceRepository            │
│                                                                   │
│   Entities: IntegrationConfig, IntegrationPushLog                │
│   Table auto-created by Hibernate (ddl-auto=update)              │
└──────────────────────────────────────────────────────────────────┘
                              │ JDBC
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│               User's Target Database (PostgreSQL / MySQL)         │
│                                                                   │
│   scanly_invoices    — invoice headers (37 rows pushed)          │
│   scanly_line_items  — line items (FK → scanly_invoices)        │
└──────────────────────────────────────────────────────────────────┘
```

---

*Implementation log last updated: October 2026 | Scanly project*
