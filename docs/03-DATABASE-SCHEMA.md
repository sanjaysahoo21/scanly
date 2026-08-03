# 🗄️ Database Schema Design

> PostgreSQL 16 — Normalized Relational Schema

---

## 1. Entity-Relationship Diagram

```
┌──────────────────┐         ┌──────────────────────────────────────┐
│   organizations  │         │               users                  │
├──────────────────┤         ├──────────────────────────────────────┤
│ id (PK)          │◄───┐    │ id (PK)                              │
│ name             │    │    │ organization_id (FK) ────────────────┘
│ created_at       │    │    │ email (UNIQUE)                       │
│ updated_at       │    │    │ password_hash                        │
└──────────────────┘    │    │ full_name                            │
                        │    │ role (ADMIN | AUDITOR | VIEWER)      │
                        │    │ created_at                           │
                        │    │ updated_at                           │
                        │    └────────────────┬─────────────────────┘
                        │                     │
                        │                     │ uploaded_by
                        │                     ▼
                        │    ┌──────────────────────────────────────┐
                        │    │            documents                 │
                        │    ├──────────────────────────────────────┤
                        │    │ id (PK)                              │
                        └────│ organization_id (FK)                 │
                             │ uploaded_by (FK → users.id)          │
                             │ file_name                            │
                             │ file_path                            │
                             │ file_type (PDF | IMAGE | TEXT)       │
                             │ file_size_bytes                      │
                             │ status (PENDING | PROCESSING |       │
                             │         COMPLETED | FAILED |         │
                             │         NEEDS_REVIEW)                │
                             │ confidence_score (0.0 – 1.0)         │
                             │ error_message                        │
                             │ raw_extracted_text                   │
                             │ ai_raw_response (JSONB)              │
                             │ retry_count                          │
                             │ created_at                           │
                             │ updated_at                           │
                             └────────────────┬─────────────────────┘
                                              │
                                              │ 1:1
                                              ▼
                             ┌──────────────────────────────────────┐
                             │            invoices                  │
                             ├──────────────────────────────────────┤
                             │ id (PK)                              │
                             │ document_id (FK, UNIQUE)             │
                             │ invoice_number                       │
                             │ vendor_name                          │
                             │ vendor_address                       │
                             │ vendor_gstin                         │
                             │ buyer_name                           │
                             │ buyer_address                        │
                             │ buyer_gstin                          │
                             │ invoice_date                         │
                             │ due_date                             │
                             │ subtotal                             │
                             │ tax_amount                           │
                             │ discount_amount                      │
                             │ total_amount                         │
                             │ currency (INR | USD | EUR)           │
                             │ is_audited (BOOLEAN, default false)  │
                             │ audited_by (FK → users.id, nullable) │
                             │ audited_at                           │
                             │ created_at                           │
                             │ updated_at                           │
                             └────────────────┬─────────────────────┘
                                              │
                                              │ 1:N
                                              ▼
                             ┌──────────────────────────────────────┐
                             │           line_items                 │
                             ├──────────────────────────────────────┤
                             │ id (PK)                              │
                             │ invoice_id (FK)                      │
                             │ description                          │
                             │ hsn_code                             │
                             │ quantity                             │
                             │ unit_price                           │
                             │ tax_rate                             │
                             │ total_price                          │
                             │ created_at                           │
                             └──────────────────────────────────────┘


                             ┌──────────────────────────────────────┐
                             │           audit_logs                 │
                             ├──────────────────────────────────────┤
                             │ id (PK)                              │
                             │ document_id (FK)                     │
                             │ invoice_id (FK, nullable)            │
                             │ user_id (FK)                         │
                             │ entity_type (INVOICE | LINE_ITEM)    │
                             │ entity_id                            │
                             │ field_name                           │
                             │ old_value                            │
                             │ new_value                            │
                             │ action (EDIT | APPROVE | REJECT)     │
                             │ created_at                           │
                             └──────────────────────────────────────┘
```

---

## 2. Full SQL Schema

### 2.1 Organizations

```sql
CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

### 2.2 Users

```sql
CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    full_name         VARCHAR(255) NOT NULL,
    role              VARCHAR(20) NOT NULL DEFAULT 'VIEWER'
                      CHECK (role IN ('ADMIN', 'AUDITOR', 'VIEWER')),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_organization ON users(organization_id);
CREATE INDEX idx_users_email ON users(email);
```

### 2.3 Documents

```sql
CREATE TABLE documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    uploaded_by         UUID NOT NULL REFERENCES users(id),
    file_name           VARCHAR(500) NOT NULL,
    file_path           VARCHAR(1000) NOT NULL,
    file_type           VARCHAR(20) NOT NULL
                        CHECK (file_type IN ('PDF', 'IMAGE', 'TEXT')),
    file_size_bytes     BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'NEEDS_REVIEW')),
    confidence_score    DECIMAL(3, 2) CHECK (confidence_score >= 0 AND confidence_score <= 1),
    error_message       TEXT,
    raw_extracted_text  TEXT,
    ai_raw_response     JSONB,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_organization ON documents(organization_id);
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX idx_documents_created_at ON documents(created_at DESC);
```

### 2.4 Invoices

```sql
CREATE TABLE invoices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID NOT NULL UNIQUE REFERENCES documents(id) ON DELETE CASCADE,
    invoice_number    VARCHAR(100),
    vendor_name       VARCHAR(500),
    vendor_address    TEXT,
    vendor_gstin      VARCHAR(20),
    buyer_name        VARCHAR(500),
    buyer_address     TEXT,
    buyer_gstin       VARCHAR(20),
    invoice_date      DATE,
    due_date          DATE,
    subtotal          DECIMAL(15, 2),
    tax_amount        DECIMAL(15, 2),
    discount_amount   DECIMAL(15, 2) DEFAULT 0.00,
    total_amount      DECIMAL(15, 2),
    currency          VARCHAR(3) NOT NULL DEFAULT 'INR'
                      CHECK (currency IN ('INR', 'USD', 'EUR', 'GBP')),
    is_audited        BOOLEAN NOT NULL DEFAULT FALSE,
    audited_by        UUID REFERENCES users(id),
    audited_at        TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_document ON invoices(document_id);
CREATE INDEX idx_invoices_vendor ON invoices(vendor_name);
CREATE INDEX idx_invoices_date ON invoices(invoice_date);
CREATE INDEX idx_invoices_audited ON invoices(is_audited);
```

### 2.5 Line Items

```sql
CREATE TABLE line_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description     VARCHAR(1000) NOT NULL,
    hsn_code        VARCHAR(20),
    quantity        DECIMAL(10, 3) NOT NULL DEFAULT 1,
    unit_price      DECIMAL(15, 2) NOT NULL,
    tax_rate        DECIMAL(5, 2) DEFAULT 0.00,
    total_price     DECIMAL(15, 2) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_line_items_invoice ON line_items(invoice_id);
```

### 2.6 Audit Logs

```sql
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID REFERENCES documents(id) ON DELETE SET NULL,
    invoice_id      UUID REFERENCES invoices(id) ON DELETE SET NULL,
    user_id         UUID NOT NULL REFERENCES users(id),
    entity_type     VARCHAR(20) NOT NULL
                    CHECK (entity_type IN ('INVOICE', 'LINE_ITEM', 'DOCUMENT')),
    entity_id       UUID NOT NULL,
    field_name      VARCHAR(100) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    action          VARCHAR(20) NOT NULL DEFAULT 'EDIT'
                    CHECK (action IN ('EDIT', 'APPROVE', 'REJECT', 'REPROCESS')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Audit logs are append-only. Never updated or deleted.
CREATE INDEX idx_audit_logs_document ON audit_logs(document_id);
CREATE INDEX idx_audit_logs_invoice ON audit_logs(invoice_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
```

---

## 3. Key Relationships

| Relationship | Type | Description |
|---|---|---|
| `organizations` → `users` | 1:N | An organization has many users |
| `organizations` → `documents` | 1:N | An organization owns many documents |
| `users` → `documents` | 1:N | A user uploads many documents |
| `documents` → `invoices` | 1:1 | Each document produces one parsed invoice |
| `invoices` → `line_items` | 1:N | An invoice has many line items |
| `documents` → `audit_logs` | 1:N | A document can have many audit entries |

---

## 4. Design Decisions

### Why UUIDs Instead of Auto-Increment IDs?

- **Security:** Sequential IDs leak information (e.g., "There are only 47 invoices").
- **Distributed Systems:** UUIDs can be generated by any service without coordination.
- **URL Safety:** UUIDs are safe to expose in API URLs.

### Why JSONB for `ai_raw_response`?

- Stores the raw AI response for debugging and reprocessing.
- JSONB is indexable and queryable in PostgreSQL.
- Allows schema evolution without migrations for AI output.

### Why Separate `documents` and `invoices` Tables?

- **Separation of concerns:** `documents` is about the file and processing state; `invoices` is about the extracted business data.
- **Extensibility:** In the future, a document could produce other entity types (purchase orders, contracts) without modifying the documents table.

### Why Append-Only Audit Logs?

- **Compliance:** Audit logs must never be modified or deleted.
- **Integrity:** Provides a complete, tamper-proof history of all data changes.
