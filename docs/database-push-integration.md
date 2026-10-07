# Database Push / Data Integration Feature

> **Status**: Planned — not yet implemented  
> **Author**: Scanly project discussion (October 2026)  
> **Purpose**: This document captures the full design, architecture, tradeoffs, and implementation plan for pushing extracted invoice data directly into a user's own database — local or cloud-hosted.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Problem Statement](#2-problem-statement)
3. [Supported Targets](#3-supported-targets)
4. [Core Challenges](#4-core-challenges)
   - 4.1 [Schema Mismatch](#41-schema-mismatch)
   - 4.2 [Unstructured / Nested Data in SQL](#42-unstructured--nested-data-in-sql)
   - 4.3 [Type Coercion](#43-type-coercion)
   - 4.4 [Connection Failures](#44-connection-failures)
   - 4.5 [Schema Evolution](#45-schema-evolution)
   - 4.6 [Partial Push Failures](#46-partial-push-failures)
5. [Two Implementation Tiers](#5-two-implementation-tiers)
   - 5.1 [Tier 1 — Auto-create (Recommended First)](#51-tier-1--auto-create-recommended-first)
   - 5.2 [Tier 2 — Push to Existing Table (Field Mapper)](#52-tier-2--push-to-existing-table-field-mapper)
6. [Full Architecture](#6-full-architecture)
7. [Backend Design](#7-backend-design)
   - 7.1 [New Entities](#71-new-entities)
   - 7.2 [API Endpoints](#72-api-endpoints)
   - 7.3 [IntegrationService Logic](#73-integrationservice-logic)
   - 7.4 [Dynamic INSERT Builder](#74-dynamic-insert-builder)
   - 7.5 [Security](#75-security)
8. [Frontend Design](#8-frontend-design)
   - 8.1 [Settings → Integrations Page](#81-settings--integrations-page)
   - 8.2 [Field Mapper UI (Tier 2)](#82-field-mapper-ui-tier-2)
   - 8.3 [Push Result / Error Report](#83-push-result--error-report)
9. [NoSQL vs SQL Differences](#9-nosql-vs-sql-differences)
10. [Type Coercion Table](#10-type-coercion-table)
11. [Push Flow Diagrams](#11-push-flow-diagrams)
12. [Recommended Implementation Order](#12-recommended-implementation-order)
13. [Open Questions](#13-open-questions)

---

## 1. Overview

**Current export behaviour:**
- User clicks "Export" → downloads a `.csv` or `.json` file to their local machine.
- No connection to any external system.

**This feature adds:**
- User configures a **target database connection** (PostgreSQL, MySQL, MongoDB, Supabase, Google Sheets, etc.) in a Settings → Integrations page.
- User maps our extracted invoice fields to their table columns (or lets us auto-create a table).
- On demand (or automatically after processing), invoice data is **pushed directly into the target database**.

This is how tools like **Zapier, Fivetran, Airbyte, and Census** work — but embedded natively into Scanly.

---

## 2. Problem Statement

Extracted invoice data currently lives only inside Scanly's own PostgreSQL database. Users who want this data in their own systems must:
1. Export CSV/JSON manually
2. Import into their system manually
3. Repeat for every batch

This creates a painful, error-prone manual loop. The goal is to **automate that last mile** — push data directly where the user needs it.

---

## 3. Supported Targets

### Phase 1 (SQL — JDBC)
| Target | Driver | Notes |
|--------|--------|-------|
| PostgreSQL | `org.postgresql:postgresql` | Already in project |
| MySQL / MariaDB | `com.mysql:mysql-connector-j` | Add dependency |
| SQLite | `org.xerial:sqlite-jdbc` | For local desktop DBs |
| AWS RDS / Azure SQL | Any JDBC driver | Cloud-hosted SQL |

### Phase 2 (SaaS — REST API)
| Target | Auth | Notes |
|--------|------|-------|
| Google Sheets | OAuth 2.0 | Sheets API v4 |
| Airtable | API key | REST API |
| Supabase | API key + REST | PostgREST under the hood |
| Notion | API key | REST API |

### Phase 3 (NoSQL)
| Target | Driver | Notes |
|--------|--------|-------|
| MongoDB | `org.mongodb:mongodb-driver-sync` | Document model — easiest push |
| Firebase Firestore | Firebase Admin SDK | Real-time + cloud |
| Redis | `io.lettuce:lettuce-core` | Hash/JSON storage |

---

## 4. Core Challenges

### 4.1 Schema Mismatch

**The problem:**

Our field names will almost never match the user's table column names.

```
Our field          User's column
──────────────    ─────────────────
invoiceNumber  ≠  bill_id
vendorName     ≠  party_name
totalAmount    ≠  amount
invoiceDate    ≠  bill_date
vendorGstin    ≠  gst_number
```

**The solution: Visual Field Mapper**

After the user enters connection details, we:
1. Introspect their table using `INFORMATION_SCHEMA.COLUMNS`
2. Fetch all column names and data types
3. Present a UI where they drag/select which of our fields maps to which of their columns
4. Save this mapping as a JSON document in our DB
5. Use the saved mapping to dynamically build `INSERT` statements

```json
// Saved mapping document (stored in integration_configs table)
{
  "targetTable": "bills",
  "fieldMap": {
    "invoiceNumber": "bill_id",
    "vendorName": "party_name",
    "totalAmount": "amount",
    "invoiceDate": "bill_date",
    "vendorGstin": "gst_number"
  },
  "lineItemsStrategy": "flatten"
}
```

---

### 4.2 Unstructured / Nested Data in SQL

**The problem:**

Invoice `lineItems` is a one-to-many array:
```json
"lineItems": [
  { "description": "Web Design", "quantity": 2, "unitPrice": 2000, "total": 4000 },
  { "description": "Hosting",    "quantity": 1, "unitPrice": 820,  "total":  820 }
]
```

SQL cannot store an array in one row. Three strategies:

| Strategy | SQL Output | Use Case |
|----------|-----------|---------|
| **Flatten** | One row per line item, invoice header repeated | ERP tables with a `lines` table |
| **JSON column** | Store as `jsonb` / `JSON` column | PostgreSQL 9.4+, MySQL 5.7+ |
| **Skip** | Don't push line items at all | Simple billing summaries |

User selects their preferred strategy in the mapper UI.

**Flatten example output:**
```
bill_id   | party_name | amount | line_desc    | line_qty | line_price
INV-042   | Acme Corp  | 4820   | Web Design   | 2        | 2000
INV-042   | Acme Corp  | 4820   | Hosting      | 1        | 820
```

**JSON column example (PostgreSQL):**
```sql
INSERT INTO bills (bill_id, party_name, amount, line_items)
VALUES ('INV-042', 'Acme Corp', 4820, '[{"desc":"Web Design",...}]'::jsonb);
```

---

### 4.3 Type Coercion

**The problem:**

Our `totalAmount` is Java `BigDecimal`. Their column might be `VARCHAR(50)`.

**The solution:** A coercion layer that converts based on target column type detected from `INFORMATION_SCHEMA`:

| Our Type | Their Column Type | Coercion |
|----------|------------------|---------|
| `BigDecimal` | `VARCHAR` | `.toString()` → `"4820.00"` |
| `LocalDate` | `BIGINT` | `.toEpochDay()` → `19638` |
| `LocalDate` | `VARCHAR` | `.toString()` → `"2024-03-15"` |
| `Boolean` | `CHAR(1)` | `true` → `"Y"`, `false` → `"N"` |
| `Boolean` | `INT` | `true` → `1`, `false` → `0` |
| `UUID` | `VARCHAR` | `.toString()` |
| `String` | `TEXT`/`VARCHAR` | Direct |
| `BigDecimal` | `DECIMAL` | Direct |
| `LocalDate` | `DATE` | Direct |

See [Section 10](#10-type-coercion-table) for the full table.

---

### 4.4 Connection Failures

**The problem:**

Raw JDBC exceptions are unreadable:
```
com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
```

**The solution:** Map JDBC exception codes to human-readable diagnostics:

| Exception / Error Code | User Message |
|----------------------|-------------|
| `Connection refused` | "Cannot reach the database. Check the host/port and ensure the database is reachable from the internet or allow Scanly's IP in your firewall." |
| `password authentication failed` | "Wrong username or password." |
| `SSL connection required` | "This database requires SSL. Add `?sslmode=require` to your connection string." |
| `database does not exist` | "Database name not found. Check the database name field." |
| `relation does not exist` | "Table not found. Check the table name or create it first." |
| Timeout | "Connection timed out after 5 seconds. The server may be unreachable or a firewall is blocking the connection." |

**Retry logic:**
```
Attempt 1 → fail → wait 1s
Attempt 2 → fail → wait 2s
Attempt 3 → fail → give up → show diagnostic
```

---

### 4.5 Schema Evolution

**The problem:**

User changes their table schema next month (adds/renames/drops columns). Our saved mapping breaks silently.

**The solution:** Re-validate mapping before every push:

```
Before push:
1. Re-introspect target table schema via INFORMATION_SCHEMA
2. Compare against saved field mapping
3. If a mapped column no longer exists:
   → Block push
   → Show warning: "Column 'bill_id' no longer exists in 'bills'. 
                    Please update your field mapping."
4. If new columns exist (not in mapping):
   → Push proceeds (unmapped columns receive NULL / default)
   → Show info: "3 new columns found. Update mapping to include them."
```

---

### 4.6 Partial Push Failures

**The problem:**

Out of 50 invoices, 47 succeed and 3 fail (e.g. value too long, duplicate key, null constraint). 
Rolling back all 50 loses good work. Committing silently hides the errors.

**The solution:** Two modes the user chooses:

| Mode | Behaviour |
|------|----------|
| **All-or-nothing** | Wrap all 50 INSERTs in one transaction. Any failure rolls back everything. Safe for idempotency. |
| **Best-effort** | Commit each INSERT individually. Failures are collected, reported, and skippable. |

After push, show a result panel:
```
Push complete
─────────────────────────────────────────────
✅ 47 invoices pushed successfully
❌  3 failed:

  INV-042: value too long for column "bill_id" (max 10 chars, got 15)
  INV-087: null value in column "amount" violates NOT NULL constraint
  INV-091: duplicate key value violates unique constraint "bills_pkey"

[Retry failed] [Skip & close] [Download error CSV]
```

---

## 5. Two Implementation Tiers

### 5.1 Tier 1 — Auto-create (Recommended First)

> *"Let us create a table in your database. Just give us a connection."*

**Flow:**
1. User enters connection details (host, port, DB, user, password)
2. Backend tests the connection
3. Backend runs DDL to create a `scanly_invoices` table (and optionally `scanly_line_items`)
4. All future pushes INSERT into that table — no mapping needed

**DDL generated:**
```sql
CREATE TABLE IF NOT EXISTS scanly_invoices (
  id               UUID PRIMARY KEY,
  invoice_number   VARCHAR(100),
  vendor_name      VARCHAR(500),
  vendor_address   TEXT,
  vendor_gstin     VARCHAR(20),
  buyer_name       VARCHAR(500),
  buyer_address    TEXT,
  buyer_gstin      VARCHAR(20),
  invoice_date     DATE,
  due_date         DATE,
  subtotal         DECIMAL(15,2),
  tax_amount       DECIMAL(15,2),
  discount_amount  DECIMAL(15,2),
  total_amount     DECIMAL(15,2),
  currency         VARCHAR(3),
  is_audited       BOOLEAN,
  has_errors       BOOLEAN,
  is_duplicate     BOOLEAN,
  pushed_at        TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scanly_line_items (
  id               UUID PRIMARY KEY,
  invoice_id       UUID REFERENCES scanly_invoices(id),
  description      TEXT,
  hsn_code         VARCHAR(20),
  quantity         DECIMAL(10,3),
  unit_price       DECIMAL(15,2),
  tax_rate         DECIMAL(5,2),
  total_price      DECIMAL(15,2)
);
```

**Pros:** Zero schema mismatch, fastest to implement, always works  
**Cons:** Creates our table structure in their DB, not their own table  
**Effort:** Low — one DDL string + straightforward INSERT logic

---

### 5.2 Tier 2 — Push to Existing Table (Field Mapper)

> *"Connect to your existing table. Map our fields to yours."*

**Flow:**
1. User enters connection + target table name
2. Backend introspects `INFORMATION_SCHEMA` → returns column list
3. Mapper UI shown — user assigns our fields to their columns
4. User configures line-items strategy (flatten / JSON / skip)
5. Mapping saved
6. On push: dynamic `INSERT` built from saved mapping + type coercion

**Pros:** Works with the user's existing schema  
**Cons:** Complex to build correctly, needs coercion layer + validation  
**Effort:** Medium-High

---

## 6. Full Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                     Scanly Frontend (React)                         │
│                                                                     │
│  Settings → Integrations                                            │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Step 1: Select Target   Step 2: Enter Credentials           │  │
│  │  [ PostgreSQL ▼ ]        Host: ____ Port: ____ DB: ____      │  │
│  │                          User: ____ Pass: ____               │  │
│  │                          [Test Connection]                   │  │
│  │                                                              │  │
│  │  Step 3: Choose Mode                                         │  │
│  │  ◉ Auto-create table (Tier 1 - Recommended)                 │  │
│  │  ○ Use existing table (Tier 2 - I'll map fields)             │  │
│  │                                                              │  │
│  │  Step 4: (Tier 2 only) Field Mapper                          │  │
│  │  Our Field         →    Their Column (from introspection)    │  │
│  │  invoiceNumber          [ bill_id ▼ ]                        │  │
│  │  vendorName             [ party_name ▼ ]                     │  │
│  │  totalAmount            [ amount ▼ ]                         │  │
│  │  lineItems              [ Strategy: Flatten ▼ ]              │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                          │ REST API
                          ▼
┌────────────────────────────────────────────────────────────────────┐
│                   Scanly Backend (Spring Boot)                       │
│                                                                     │
│  IntegrationController                                              │
│    POST /api/v1/integrations          → save config                 │
│    POST /api/v1/integrations/{id}/test → test connection            │
│    GET  /api/v1/integrations/{id}/schema → introspect columns       │
│    POST /api/v1/integrations/{id}/push → push invoice IDs           │
│    GET  /api/v1/integrations          → list configs for org        │
│    DELETE /api/v1/integrations/{id}   → remove config               │
│                                                                     │
│  IntegrationService                                                 │
│    - Decrypt credentials (AES-256)                                  │
│    - Build JDBC DataSource on the fly                               │
│    - INFORMATION_SCHEMA introspection                               │
│    - DDL generation (Tier 1)                                        │
│    - Dynamic INSERT builder + type coercion (Tier 2)                │
│    - Error classification & retry                                   │
│    - Push result logging                                            │
└────────────────────────────────────────────────────────────────────┘
                          │ JDBC / REST
                          ▼
┌────────────────────────────────────────────────────────────────────┐
│              User's Target Database (Local or Cloud)                │
│                                                                     │
│  PostgreSQL  │  MySQL  │  MongoDB  │  Supabase  │  Google Sheets    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 7. Backend Design

### 7.1 New Entities

#### `IntegrationConfig`
```java
@Entity
@Table(name = "integration_configs")
public class IntegrationConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;

    @Column(nullable = false, length = 50)
    private String dbType; // "postgresql", "mysql", "mongodb", "sheets"

    @Column(nullable = false, length = 500)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false, length = 200)
    private String databaseName;

    @Column(nullable = false, length = 200)
    private String username;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword; // AES-256 encrypted

    @Column(length = 200)
    private String targetTable; // null = auto-create mode

    @Column(columnDefinition = "jsonb")
    private String fieldMapping; // JSON: { "invoiceNumber": "bill_id", ... }

    @Column(length = 20)
    @Builder.Default
    private String lineItemsStrategy = "skip"; // "flatten", "json", "skip"

    @Column(length = 20)
    @Builder.Default
    private String pushMode = "best_effort"; // "all_or_nothing", "best_effort"

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
```

#### `IntegrationPushLog`
```java
@Entity
@Table(name = "integration_push_logs")
public class IntegrationPushLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    private IntegrationConfig integration;

    @Column(nullable = false)
    private Integer totalAttempted;

    @Column(nullable = false)
    private Integer totalSucceeded;

    @Column(nullable = false)
    private Integer totalFailed;

    @Column(columnDefinition = "jsonb")
    private String failedInvoices; // JSON array of { id, error }

    @Column(nullable = false)
    private Instant pushedAt;

    @Column(length = 20)
    private String status; // "SUCCESS", "PARTIAL", "FAILED"
}
```

---

### 7.2 API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/integrations` | List all integrations for the org |
| `POST` | `/api/v1/integrations` | Create a new integration config |
| `PUT` | `/api/v1/integrations/{id}` | Update an integration config |
| `DELETE` | `/api/v1/integrations/{id}` | Remove an integration |
| `POST` | `/api/v1/integrations/{id}/test` | Test the connection (returns OK / error message) |
| `GET` | `/api/v1/integrations/{id}/schema` | Introspect target table columns (Tier 2) |
| `POST` | `/api/v1/integrations/{id}/push` | Push selected invoice IDs |
| `GET` | `/api/v1/integrations/{id}/logs` | Get push history |

**Push request body:**
```json
{
  "invoiceIds": ["uuid1", "uuid2", "..."],
  "pushAll": false
}
```

**Push response:**
```json
{
  "status": "PARTIAL",
  "totalAttempted": 50,
  "totalSucceeded": 47,
  "totalFailed": 3,
  "failures": [
    { "invoiceId": "uuid-x", "invoiceNumber": "INV-042", "error": "value too long for column 'bill_id'" },
    { "invoiceId": "uuid-y", "invoiceNumber": "INV-087", "error": "null value violates NOT NULL constraint" }
  ]
}
```

---

### 7.3 IntegrationService Logic

```java
// Pseudocode — not production code
@Service
public class IntegrationService {

    // Build a live JDBC DataSource from decrypted config
    private DataSource buildDataSource(IntegrationConfig config) {
        String password = cryptoService.decrypt(config.getEncryptedPassword());
        String url = buildJdbcUrl(config.getDbType(), config.getHost(),
                                  config.getPort(), config.getDatabaseName());
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(config.getUsername());
        hc.setPassword(password);
        hc.setConnectionTimeout(5_000);  // 5 second timeout
        hc.setMaximumPoolSize(3);
        return new HikariDataSource(hc);
    }

    // Test the connection — return user-friendly message on failure
    public ConnectionTestResult testConnection(IntegrationConfig config) {
        try (Connection conn = buildDataSource(config).getConnection()) {
            conn.createStatement().executeQuery("SELECT 1");
            return ConnectionTestResult.success();
        } catch (SQLException ex) {
            return ConnectionTestResult.failure(classify(ex));
        }
    }

    // Tier 1 — auto-create our table in their DB
    public void ensureTable(IntegrationConfig config, JdbcTemplate tgt) {
        String ddl = DDLGenerator.generateFor(config.getDbType());
        tgt.execute(ddl);
    }

    // Push invoice list
    public PushResult push(IntegrationConfig config, List<UUID> invoiceIds) {
        DataSource tgt = buildDataSource(config);
        JdbcTemplate tgtJdbc = new JdbcTemplate(tgt);

        List<InvoiceResponse> invoices = invoiceRepository.findAllById(invoiceIds)
            .stream().map(InvoiceResponse::from).toList();

        List<PushFailure> failures = new ArrayList<>();
        int succeeded = 0;

        for (InvoiceResponse inv : invoices) {
            try {
                if (config.getTargetTable() == null) {
                    // Tier 1: insert into our pre-defined table
                    insertTier1(tgtJdbc, inv);
                } else {
                    // Tier 2: dynamic insert with field mapping + coercion
                    insertTier2(tgtJdbc, inv, config);
                }
                succeeded++;
            } catch (DataAccessException ex) {
                failures.add(new PushFailure(inv.getId(), inv.getInvoiceNumber(),
                                             classifyInsertError(ex)));
            }
        }
        // Save log and return result
        savePushLog(config, invoices.size(), succeeded, failures);
        return new PushResult(invoices.size(), succeeded, failures);
    }
}
```

---

### 7.4 Dynamic INSERT Builder

Used in Tier 2 when the user has their own table and a saved field mapping:

```java
private void insertTier2(JdbcTemplate tgt, InvoiceResponse inv, IntegrationConfig config) {
    Map<String, String> fieldMap = parseFieldMap(config.getFieldMapping());
    // fieldMap: { "invoiceNumber" -> "bill_id", "vendorName" -> "party_name", ... }

    List<String> columns = new ArrayList<>();
    List<Object> values  = new ArrayList<>();

    for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
        String ourField  = entry.getKey();    // "invoiceNumber"
        String theirCol  = entry.getValue();  // "bill_id"
        Object rawValue  = getFieldValue(inv, ourField);
        String theirType = columnTypeCache.get(config.getId(), theirCol); // from INFORMATION_SCHEMA

        columns.add(theirCol);
        values.add(TypeCoercion.coerce(rawValue, theirType));
    }

    String sql = "INSERT INTO " + sanitizeTableName(config.getTargetTable()) +
                 " (" + String.join(", ", columns) + ") VALUES (" +
                 columns.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";

    tgt.update(sql, values.toArray());
}
```

---

### 7.5 Security

| Concern | Mitigation |
|---------|-----------|
| **Credential storage** | Passwords encrypted with AES-256-GCM before saving to DB. Decrypted only in-memory at push time. Never returned in API responses. |
| **SSRF (Server-Side Request Forgery)** | Validate that host is not a private IP range (`10.x`, `172.16.x`, `192.168.x`, `127.x`, `::1`) unless explicitly allowed. Whitelist allowed ports: `5432`, `3306`, `27017`, `443`, `5433`. |
| **SQL Injection in table/column names** | Sanitize table and column names to `[a-zA-Z0-9_]` only before inserting into SQL string. Never accept raw column names from user in WHERE clauses. |
| **Connection pooling leak** | DataSources created per-push are closed immediately after. Never cached long-term. |
| **Credentials in logs** | Password field masked before any logging. |

---

## 8. Frontend Design

### 8.1 Settings → Integrations Page

**Route:** `/settings/integrations`

**UI layout:**
```
Settings / Integrations

[+ Add Integration]

┌─────────────────────────────────────────────────┐
│ 🐘 PostgreSQL            my-erp.internal:5432   │
│ Table: bills    ● Active   Last push: 2 hrs ago │
│ ✅ 142 pushed  ❌ 0 failed                       │
│ [Push Now] [Edit] [Delete]                       │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ 📊 Google Sheets         Q4 Invoice Tracker     │
│ ● Active   Last sync: yesterday                 │
│ [Push Now] [Edit] [Delete]                       │
└─────────────────────────────────────────────────┘
```

**Add Integration Wizard (3 steps):**
1. **Select type** — PostgreSQL / MySQL / MongoDB / Google Sheets / Airtable
2. **Enter credentials** — host, port, database, user, password + [Test Connection] button
3. **Table mode** — Auto-create (Tier 1) or Choose existing table (Tier 2, shows mapper)

---

### 8.2 Field Mapper UI (Tier 2)

After entering connection details and selecting "Use existing table":

```
Map Fields — bills

Our Data Field          →   Your Column (from bills table)
──────────────────────────────────────────────────────────
Invoice Number          →   [ bill_id        ▼ ] VARCHAR(50)
Vendor Name             →   [ party_name     ▼ ] VARCHAR(200)
Total Amount            →   [ amount         ▼ ] DECIMAL(12,2)
Invoice Date            →   [ bill_date      ▼ ] DATE
Vendor GSTIN            →   [ gst_no         ▼ ] VARCHAR(20)
Buyer Name              →   [ (skip)         ▼ ]
Tax Amount              →   [ (skip)         ▼ ]

Line Items Strategy:   ◉ Skip  ○ Flatten into rows  ○ JSON column
Push Mode:             ◉ Best effort  ○ All or nothing

[Save Mapping]
```

---

### 8.3 Push Result / Error Report

```
Push Complete — PostgreSQL (my-erp.internal)
─────────────────────────────────────────────
✅  47 invoices pushed successfully
❌   3 failed

  INV-042   value too long for column "bill_id" (max 10 chars, got 15)
  INV-087   null value in column "amount" violates NOT NULL constraint
  INV-091   duplicate key value — "INV-091" already exists in bills

[Retry failed] [Skip & close] [Download error CSV]
```

---

## 9. NoSQL vs SQL Differences

| Aspect | SQL (PostgreSQL/MySQL) | NoSQL (MongoDB) |
|--------|----------------------|----------------|
| **Schema** | Rigid — columns must exist beforehand | Flexible — any fields accepted |
| **Line items** | Needs flatten/JSON column strategy | Natural — nested array in document |
| **Schema mismatch** | Type errors, column-not-found errors | Rarely fails — just stores whatever |
| **Introspection** | `INFORMATION_SCHEMA.COLUMNS` | `db.collection.findOne()` to infer |
| **Dynamic INSERT** | `INSERT INTO table (col1, col2) VALUES (?, ?)` | `collection.insertOne(document)` |
| **Mapping needed?** | Yes — field names rarely match | Optional — can push as-is |
| **Implementation difficulty** | Medium (coercion + mapping) | Low (just push BSON document) |

**MongoDB is significantly simpler to push to** because there is no schema enforcement. You can push the entire `InvoiceResponse` JSON as-is with a single `insertOne()` call — no mapping, no coercion.

```java
// MongoDB push (Tier 1 — no mapping needed)
MongoCollection<Document> collection = mongoClient
    .getDatabase(config.getDatabaseName())
    .getCollection("scanly_invoices");

Document doc = Document.parse(objectMapper.writeValueAsString(invoiceResponse));
doc.append("pushed_at", new Date());
collection.insertOne(doc);
```

---

## 10. Type Coercion Table

| Our Java Type | Target SQL Type | Coercion Applied |
|---------------|----------------|-----------------|
| `BigDecimal` | `VARCHAR`, `CHAR` | `.toString()` → `"4820.00"` |
| `BigDecimal` | `DECIMAL`, `NUMERIC` | Direct |
| `BigDecimal` | `BIGINT`, `INT` | `.longValue()` |
| `BigDecimal` | `FLOAT`, `DOUBLE` | `.doubleValue()` |
| `LocalDate` | `DATE` | Direct |
| `LocalDate` | `TIMESTAMP` | `.atStartOfDay()` |
| `LocalDate` | `BIGINT` | `.toEpochDay()` |
| `LocalDate` | `VARCHAR` | `.toString()` → `"2024-03-15"` |
| `Instant` | `TIMESTAMP` | `Timestamp.from(instant)` |
| `Instant` | `BIGINT` | `.toEpochMilli()` |
| `Boolean` | `BOOLEAN`, `BIT` | Direct |
| `Boolean` | `CHAR(1)` | `true` → `"Y"`, `false` → `"N"` |
| `Boolean` | `INT`, `TINYINT` | `true` → `1`, `false` → `0` |
| `UUID` | `UUID` | Direct (PostgreSQL) |
| `UUID` | `VARCHAR`, `CHAR` | `.toString()` |
| `String` | `TEXT`, `VARCHAR` | Direct (truncate if too long + warn) |
| `List<LineItem>` | `JSON`, `JSONB` | `objectMapper.writeValueAsString()` |
| `null` | any | `null` — let DB enforce NOT NULL constraint |

---

## 11. Push Flow Diagrams

### Tier 1 — Auto-create Flow
```
User clicks [Push Now]
       │
       ▼
Decrypt credentials
       │
       ▼
Build JDBC DataSource (5s timeout)
       │
   ┌───┴─────────┐
 Fail           Success
   │               │
Show error     Run DDL: CREATE TABLE IF NOT EXISTS scanly_invoices (...)
diagnostic         │
                   ▼
              INSERT each invoice row
                   │
         ┌─────────┴──────────┐
       Error              Success
         │                    │
    Collect failure      Increment counter
    with classifiedmsg        │
                              ▼
                    Save push log
                    Show result panel
```

### Tier 2 — Existing Table Flow
```
User clicks [Push Now]
       │
       ▼
Decrypt credentials + load saved mapping
       │
       ▼
Re-introspect INFORMATION_SCHEMA
       │
  ┌────┴────────────┐
Schema changed?    Schema OK
       │               │
  Block push + warn    ▼
                  For each invoice:
                    Extract mapped fields
                    Apply type coercion
                    Build dynamic INSERT SQL
                    Execute
                    Collect result
                       │
                       ▼
                  Save push log
                  Show result panel
```

---

## 12. Recommended Implementation Order

### ✅ Phase 1 — PostgreSQL + MySQL Auto-create (Tier 1)
**Effort: ~3–4 days**
- [ ] `IntegrationConfig` entity + migration
- [ ] `IntegrationPushLog` entity + migration
- [ ] `AES256CryptoService` for password encryption
- [ ] `IntegrationService.testConnection()` with error classification
- [ ] `IntegrationService.push()` — Tier 1 path only
- [ ] DDL generator for PostgreSQL + MySQL
- [ ] `IntegrationController` endpoints
- [ ] Settings → Integrations UI (list + add + test + push)
- [ ] Push result panel

### Phase 2 — Field Mapper + Existing Table (Tier 2)
**Effort: ~4–5 days**
- [ ] `INFORMATION_SCHEMA` introspection endpoint
- [ ] Type coercion layer
- [ ] Dynamic INSERT builder
- [ ] Field Mapper UI (step in wizard)
- [ ] Mapping persistence + validation before push

### Phase 3 — MongoDB
**Effort: ~1–2 days**
- [ ] MongoDB driver dependency
- [ ] `IntegrationService` MongoDB branch (insertOne)
- [ ] UI: add MongoDB to type dropdown

### Phase 4 — Google Sheets
**Effort: ~2–3 days**
- [ ] Google OAuth 2.0 flow (store refresh token encrypted)
- [ ] Sheets API v4 integration — append rows
- [ ] Column header detection from spreadsheet

---

## 13. Open Questions

| # | Question | Notes |
|---|---------|-------|
| 1 | Should credentials be stored encrypted in our DB, or only stored client-side and sent on each push request? | Storing encrypted is more convenient (scheduled pushes possible). Client-side is more secure but no scheduling. |
| 2 | Should we support **scheduled auto-push** (e.g. "push every night at 10pm")? | Needs a cron/scheduler. Nice feature but adds complexity. |
| 3 | Should we **upsert** (INSERT OR UPDATE) or always INSERT? | Upsert avoids duplicate rows if pushed twice. Requires knowing the primary key on their table. |
| 4 | What JDBC connection string options to expose? (SSL mode, connection params) | Start with sslmode toggle. Add advanced options later. |
| 5 | Should we support **multiple integrations per org** (e.g. push to both their ERP and their analytics DB)? | Yes — `IntegrationConfig` is already org-scoped and allows many rows. |
| 6 | For Google Sheets — create a new sheet per push, or append to one master sheet? | Append to one master sheet is cleaner. Allow user to configure sheet name + tab. |
| 7 | Should failed pushes be **automatically retried** (e.g. next day)? | Opt-in setting per integration. |

---

*Document written: October 2026 | Scanly project*
