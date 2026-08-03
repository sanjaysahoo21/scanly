# 🏗️ System Architecture

---

## 1. High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                                 │
│                                                                     │
│   ┌──────────────────────┐    ┌──────────────────────────────┐      │
│   │  React Frontend      │    │  Audit Dashboard             │      │
│   │  (File Upload UI)    │    │  (Side-by-Side Review)       │      │
│   └──────────┬───────────┘    └──────────────┬───────────────┘      │
│              │                               │                      │
└──────────────┼───────────────────────────────┼──────────────────────┘
               │  HTTP/REST                    │  HTTP/REST
               ▼                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        API LAYER                                    │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────┐      │
│   │  Spring Boot REST API                                    │      │
│   │  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐  │      │
│   │  │ Document   │  │ Invoice     │  │ Audit            │  │      │
│   │  │ Controller │  │ Controller  │  │ Controller       │  │      │
│   │  └─────┬──────┘  └──────┬──────┘  └────────┬─────────┘  │      │
│   │        │                │                   │            │      │
│   │  ┌─────▼──────────────────────────────────────────────┐  │      │
│   │  │              Service Layer                         │  │      │
│   │  │  DocumentService │ InvoiceService │ AuditService   │  │      │
│   │  └─────┬──────────────────────────────────────────────┘  │      │
│   └────────┼─────────────────────────────────────────────────┘      │
│            │                                                        │
└────────────┼────────────────────────────────────────────────────────┘
             │
             ├── Save File to Disk/S3
             ├── Persist metadata to PostgreSQL
             └── Publish task to Message Queue
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     MESSAGE QUEUE LAYER                              │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────┐      │
│   │  RabbitMQ / Redis Streams                                │      │
│   │                                                          │      │
│   │  Queue: document.parse.queue                             │      │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐                 │      │
│   │  │ Task #1  │ │ Task #2  │ │ Task #3  │  ...             │      │
│   │  └──────────┘ └──────────┘ └──────────┘                 │      │
│   └──────────────────────────┬───────────────────────────────┘      │
│                              │                                      │
└──────────────────────────────┼──────────────────────────────────────┘
                               │  Consume
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    WORKER / PROCESSING LAYER                        │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────┐      │
│   │  Document Parser Engine (Background Worker)              │      │
│   │                                                          │      │
│   │  Step 1: OCR / Text Extraction                           │      │
│   │    └─ Apache Tika (PDFs) + Tesseract (Scanned Images)    │      │
│   │                                                          │      │
│   │  Step 2: AI Structured Parsing                           │      │
│   │    └─ Spring AI + Gemini API (JSON Schema enforced)      │      │
│   │                                                          │      │
│   │  Step 3: Validation & Business Rules                     │      │
│   │    └─ Field validation, total recalculation, etc.        │      │
│   │                                                          │      │
│   │  Step 4: Persist to Database                             │      │
│   │    └─ Save Invoice + Line Items to PostgreSQL            │      │
│   └──────────────────────────────────────────────────────────┘      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      DATA LAYER                                     │
│                                                                     │
│   ┌──────────────────┐    ┌────────────────────────────────┐        │
│   │  PostgreSQL 16   │    │  File Storage (Disk / S3)      │        │
│   │                  │    │                                │        │
│   │  - users         │    │  /uploads/                     │        │
│   │  - organizations │    │    ├── doc_abc123.pdf          │        │
│   │  - documents     │    │    ├── doc_def456.jpg          │        │
│   │  - invoices      │    │    └── ...                     │        │
│   │  - line_items    │    │                                │        │
│   │  - audit_logs    │    │                                │        │
│   └──────────────────┘    └────────────────────────────────┘        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Responsibilities

### 2.1 React Frontend

| Responsibility | Details |
|---|---|
| File Upload | Drag-and-drop UI for uploading PDF/JPG/PNG files (single or batch) |
| Job Tracking | Poll or subscribe (via WebSocket/SSE) for processing status updates |
| Audit Dashboard | Side-by-side view: original document (left) + editable extracted fields (right) |
| Authentication | Login/signup forms, JWT token management |

### 2.2 Spring Boot REST API

| Responsibility | Details |
|---|---|
| File Reception | Accept multipart file uploads, validate file type and size |
| Job Creation | Store file, create `document` record with `PENDING` status, return `job_id` |
| Queue Publishing | Push parsing tasks to RabbitMQ/Redis for async processing |
| CRUD Endpoints | Expose endpoints for invoices, documents, audit logs |
| Authentication | JWT-based auth with Spring Security |
| Status Polling | Endpoints to check document processing status by `job_id` |

### 2.3 Message Queue (RabbitMQ / Redis)

| Responsibility | Details |
|---|---|
| Decoupling | Separates the fast HTTP layer from the slow AI processing layer |
| Backpressure | Prevents overloading the AI API with too many concurrent requests |
| Retry Logic | Failed tasks can be retried automatically with exponential backoff |
| Dead Letter Queue | Permanently failed tasks are moved to a DLQ for manual review |

### 2.4 Document Parser Engine (Worker)

| Responsibility | Details |
|---|---|
| Text Extraction | Uses Apache Tika for native PDFs, Tesseract OCR for scanned images |
| AI Parsing | Sends extracted text to Gemini/OpenAI with a strict JSON schema prompt |
| Validation | Validates extracted fields: required fields present, amounts are numeric, dates are valid |
| Business Rules | Cross-checks line item totals against invoice total, flags discrepancies |
| Persistence | Saves validated invoice data and line items to PostgreSQL |
| Status Updates | Updates `document.status` to `COMPLETED`, `FAILED`, or `NEEDS_REVIEW` |

### 2.5 PostgreSQL Database

| Responsibility | Details |
|---|---|
| Relational Storage | Normalized schema for users, documents, invoices, line items, audit logs |
| Data Integrity | Foreign keys, constraints, and indexes for referential integrity |
| Audit Trail | Immutable audit log table tracking all human edits |

---

## 3. Data Flow: Document Upload to Audit

```
User                Frontend              API               Queue             Worker            Database
 │                    │                    │                   │                 │                  │
 │── Upload PDF ─────►│                    │                   │                 │                  │
 │                    │── POST /documents─►│                   │                 │                  │
 │                    │                    │── Save file ──────┼─────────────────┼────────────────► │
 │                    │                    │── Create record ──┼─────────────────┼────────────────► │
 │                    │                    │── Publish task ──►│                 │                  │
 │                    │◄─ 202 + job_id ────│                   │                 │                  │
 │◄─ "Processing..." ─│                    │                   │                 │                  │
 │                    │                    │                   │── Consume ─────►│                  │
 │                    │                    │                   │                 │── OCR/Tika ─────►│
 │                    │                    │                   │                 │── AI Parse ─────►│
 │                    │                    │                   │                 │── Validate ─────►│
 │                    │                    │                   │                 │── Save Invoice ─►│
 │                    │                    │                   │                 │── Update Status ►│
 │                    │── GET /documents/id│                   │                 │                  │
 │                    │◄─ status:COMPLETED─│                   │                 │                  │
 │◄── Show Results ───│                    │                   │                 │                  │
 │                    │                    │                   │                 │                  │
 │── Review & Edit ──►│                    │                   │                 │                  │
 │                    │── PUT /invoices/id─►│                  │                 │                  │
 │                    │                    │── Update fields ──┼─────────────────┼────────────────► │
 │                    │                    │── Create audit ───┼─────────────────┼────────────────► │
 │                    │◄─── 200 OK ────────│                   │                 │                  │
 │◄── "Saved!" ───────│                    │                   │                 │                  │
```

---

## 4. Design Principles

| Principle | Application |
|---|---|
| **Separation of Concerns** | Each layer (API, Queue, Worker, DB) has a single responsibility |
| **Asynchronous by Default** | Heavy processing is always offloaded to background workers |
| **Fail Gracefully** | Dead letter queues, retry policies, and meaningful error states |
| **Auditability** | Every data mutation is logged with before/after values |
| **Stateless API** | JWT authentication; no server-side sessions |
| **Containerized** | All services run in Docker containers for reproducible environments |

---

## 5. Deployment Topology

```
┌─── Docker Compose ──────────────────────────────────────────────┐
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ frontend    │  │ backend-api │  │ worker (same Spring     │  │
│  │ (nginx)     │  │ (Spring     │  │ Boot app or separate    │  │
│  │ port: 3000  │  │  Boot)      │  │ module listening to     │  │
│  │             │  │ port: 8080  │  │ RabbitMQ)               │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ PostgreSQL  │  │ RabbitMQ    │  │ (Optional) MinIO/S3     │  │
│  │ port: 5432  │  │ port: 5672  │  │ port: 9000              │  │
│  │             │  │ mgmt: 15672 │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```
