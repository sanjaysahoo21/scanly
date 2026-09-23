# Scanly — AI-Powered Invoice Management System

> **Final Year Project** — Intelligent invoice processing platform that extracts, validates, audits, and exports invoice data using AI and OCR.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Running the Application](#running-the-application)
  - [Full Stack with Docker (Recommended)](#full-stack-with-docker-recommended)
  - [Frontend Only (Development)](#frontend-only-development)
  - [Backend Only (Local Development)](#backend-only-local-development)
- [API Reference](#api-reference)
- [Key Workflows](#key-workflows)
- [Configuration](#configuration)
- [Database](#database)
- [Security](#security)
- [Contributing](#contributing)

---

## Overview

**Scanly** is a full-stack invoice intelligence platform designed to eliminate manual data entry. Users upload PDF or image invoices; Scanly automatically extracts structured data (vendor, buyer, line items, totals, taxes) using a combination of OCR and a large language model, then stores and presents the information in a clean, searchable dashboard.

The system supports multi-user organisations, role-based access, full audit logging, data validation, duplicate detection, and flexible CSV/JSON export.

---

## Features

### Document Management
- Upload PDF, JPG, and PNG invoices (up to 10MB each)
- Real-time document status tracking (PENDING → PROCESSING → COMPLETED / FAILED)
- Auto-polling on the Documents page refreshes results every 5 seconds while processing

### AI-Powered Data Extraction
- **PDF invoices** — text extracted with Apache PDFBox, structured by Groq LLM
- **Image invoices** — text extracted with Tesseract OCR (bundled in Docker), then structured by Groq LLM
- Outputs: vendor details, buyer details, GSTIN, invoice number, dates, line items, subtotal, tax, discount, total

### Invoice Management
- Paginated, searchable, and filterable invoice table
- Rich filters: date range, amount range, text search, audit status, sort order
- Row selection for bulk operations
- Per-invoice and bulk CSV/JSON export

### Audit Logging
- Every upload, process event, edit, approval, and rejection is logged
- Audit log table shows: user, document filename, entity type, field changed, old/new value, action, timestamp
- Filterable by action type; exportable to CSV or JSON

### Dashboard
- Summary statistics: total documents, total invoices, processed today, pending count
- Stacked progress bar showing document status distribution (Completed / Processing / Pending / Needs Review / Failed)
- Recent activity feed

### Validation & Duplicate Detection
- Math and tax validation on every extracted invoice
- Duplicate detection by vendor + amount + date matching
- Manual re-validation endpoint

### Security
- JWT-based authentication (register/login)
- All routes are organisation-scoped — users only see their own organisation's data
- Passwords hashed with BCrypt

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | React 18, Vite, React Router, Lucide Icons, Vanilla CSS |
| **Backend** | Spring Boot 4.1 (Java 21), Spring Security, Spring Data JPA |
| **Database** | PostgreSQL 16 |
| **AI / LLM** | Groq API (`openai/gpt-oss-120b` or configurable) |
| **OCR** | Tesseract 4 via Tess4J (bundled inside Docker image) |
| **PDF Parsing** | Apache PDFBox |
| **Containerisation** | Docker, Docker Compose |
| **ORM** | Hibernate 7 / JPA |
| **Auth** | JWT (JJWT library) |

---

## Architecture

```
┌─────────────────────────────────────────┐
│               Browser                   │
│  React SPA (Vite dev server :5173)      │
│  Proxies /api/* → backend :8081         │
└────────────────────┬────────────────────┘
                     │ HTTP/JSON
┌────────────────────▼────────────────────┐
│         Spring Boot API (:8081)         │
│  ┌──────────────┐  ┌───────────────┐   │
│  │  Controllers │  │   Services    │   │
│  │  /auth       │  │  Document     │   │
│  │  /documents  │  │  Invoice      │   │
│  │  /invoices   │  │  AuditLog     │   │
│  │  /audit-logs │  │  GroqAI       │   │
│  │  /dashboard  │  │  Export       │   │
│  └──────────────┘  └───────┬───────┘   │
│                            │           │
│  ┌─────────────────────────▼─────────┐ │
│  │  External APIs / OCR              │ │
│  │  • Groq LLM API (HTTPS)          │ │
│  │  • Tesseract OCR (in-process)    │ │
│  └───────────────────────────────────┘ │
└────────────────────┬────────────────────┘
                     │ JDBC
┌────────────────────▼────────────────────┐
│         PostgreSQL 16 (:5432)           │
│  Tables: users, organizations,          │
│          documents, invoices,           │
│          line_items, audit_logs         │
└─────────────────────────────────────────┘
```

In Docker Compose, the backend and database run in an isolated Docker network. The host maps port `5433 → 5432` (so it doesn't conflict with any locally installed PostgreSQL), and `8081 → 8081` for the API.

---

## Project Structure

```
final-year-project/
├── backend/                         # Spring Boot application
│   ├── Dockerfile                   # Multi-stage build with Tesseract OCR
│   ├── pom.xml
│   └── src/main/java/com/scanly/backend/
│       ├── controller/              # REST controllers
│       │   ├── AuthController.java
│       │   ├── DocumentController.java
│       │   ├── DocumentDetailController.java
│       │   ├── InvoiceController.java
│       │   ├── AuditLogController.java
│       │   ├── DashboardController.java
│       │   └── HealthController.java
│       ├── service/                 # Business logic
│       │   ├── DocumentService.java
│       │   ├── DocumentProcessingService.java
│       │   ├── GroqAiService.java   # LLM + OCR integration
│       │   ├── InvoiceValidationService.java
│       │   ├── DuplicateDetectionService.java
│       │   ├── AuditService.java
│       │   └── ExportService.java
│       ├── entity/                  # JPA entities
│       │   ├── User.java
│       │   ├── Organization.java
│       │   ├── Document.java
│       │   ├── Invoice.java
│       │   ├── LineItem.java
│       │   └── AuditLog.java
│       ├── dto/                     # API response DTOs
│       │   ├── InvoiceResponse.java
│       │   ├── AuditLogResponse.java
│       │   ├── DashboardStatsDto.java
│       │   └── UploadResponse.java
│       ├── repository/              # Spring Data JPA repositories
│       ├── security/                # JWT filter, UserDetailsService
│       └── config/                  # CORS, security, upload config
│
├── frontend/                        # React application
│   ├── src/
│   │   ├── pages/
│   │   │   ├── DashboardPage.jsx
│   │   │   ├── DocumentsPage.jsx
│   │   │   ├── DocumentUploadPage.jsx
│   │   │   ├── DocumentDetailPage.jsx
│   │   │   ├── InvoicesPage.jsx
│   │   │   ├── InvoiceDetailPage.jsx
│   │   │   ├── AuditLogsPage.jsx
│   │   │   ├── LoginPage.jsx
│   │   │   └── RegisterPage.jsx
│   │   ├── components/              # Reusable UI components
│   │   ├── services/                # API service layer (fetch wrappers)
│   │   └── styles/                  # Vanilla CSS per page/component
│   └── vite.config.js
│
├── docker-compose.yml               # Full stack orchestration
├── .env.example                     # Environment variable template
└── README.md
```

---

## Prerequisites

- **Docker Desktop** (with Docker Compose v2) — [Install](https://www.docker.com/products/docker-desktop/)
- **Node.js 20+** and **npm** — for running the frontend locally
- **Java 21+** and **Maven** — only needed if running the backend outside Docker
- A **Groq API key** — free at [console.groq.com](https://console.groq.com)

---

## Environment Setup

1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and fill in your values:

   ```env
   # PostgreSQL (Docker container — mapped to host port 5433)
   DB_USERNAME=postgres
   DB_PASSWORD=your_strong_password
   POSTGRES_DB=scanly
   DB_PORT=5433

   # JWT — generate a random base64 key (at least 256 bits)
   SCANLY_JWT_SECRET=your_base64_encoded_secret_here
   SCANLY_JWT_EXPIRATION_MS=86400000   # 24 hours

   # Groq AI
   GROQ_API_KEY=gsk_xxxxxxxxxxxxxxxxxxxxxxxx
   GROQ_MODEL=openai/gpt-oss-120b
   ```

   > **Note:** The database runs on port **5433** on your host machine to avoid conflicts with any locally installed PostgreSQL (which uses 5432 by default). Inside Docker, containers communicate on the standard port 5432.

---

## Running the Application

### Full Stack with Docker (Recommended)

This starts both the database and the backend (with Tesseract OCR bundled):

```bash
# First time, or after backend code changes:
docker compose up --build -d

# Subsequent starts (no code changes):
docker compose up -d
```

Then start the frontend:

```bash
cd frontend
npm install       # first time only
npm run dev
```

Open [http://localhost:5173](http://localhost:5173) in your browser.

**Useful Docker commands:**

```bash
# View backend logs (live)
docker compose logs backend -f

# View database logs
docker compose logs db -f

# Stop all containers
docker compose down

# Stop and wipe the database volume (full reset)
docker compose down -v
```

---

### Frontend Only (Development)

If the backend is already running (Docker or local):

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies all `/api/*` requests to `http://localhost:8081`.

---

### Backend Only (Local Development)

Use this mode to run only the database in Docker, while running the backend with Maven:

```bash
# Start only the database
docker compose up db -d

# In a separate terminal, run the backend
cd backend
./mvnw spring-boot:run
```

> Ensure your `.env` file has `DB_URL=jdbc:postgresql://127.0.0.1:5433/scanly` when running locally (not `db:5432` which is the Docker-internal hostname).

---

## API Reference

All endpoints require a `Bearer <token>` JWT in the `Authorization` header, except `/api/v1/auth/**`.

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/register` | Register a new user & organisation |
| `POST` | `/api/v1/auth/login` | Login, returns JWT |

### Documents

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/documents/upload` | Upload one or more PDF/JPG/PNG files |
| `GET` | `/api/v1/documents` | List all documents for the org |
| `GET` | `/api/v1/documents/{id}` | Get a single document |

### Invoices

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/invoices` | List/search invoices (`?q=`, `?dateFrom=`, `?amountMin=`, `?sort=`, etc.) |
| `GET` | `/api/v1/invoices/{id}` | Get a single invoice with line items |
| `POST` | `/api/v1/invoices/{id}/validate` | Re-run math/tax validation |
| `POST` | `/api/v1/invoices/{id}/detect-duplicates` | Re-run duplicate detection |
| `GET` | `/api/v1/invoices/export` | Export all invoices (`?format=csv\|json`) |
| `GET` | `/api/v1/invoices/{id}/export` | Export a single invoice |
| `POST` | `/api/v1/invoices/export/batch` | Export selected invoices by ID list |

### Audit Logs

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/audit-logs` | List audit logs (`?action=EDIT\|UPLOAD\|PROCESS_COMPLETE\|...`, `?page=`, `?size=`) |
| `GET` | `/api/v1/audit-logs/export` | Export audit logs (`?format=csv\|json`, `?action=`) |

### Dashboard

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/dashboard/stats` | Summary stats (totals, pending count, status breakdown) |

### Health

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/actuator/health` | Spring Boot health endpoint |

---

## Key Workflows

### Invoice Upload & Processing

```
User uploads file
      │
      ▼
DocumentService.uploadDocuments()
  → Validates file type (PDF/JPG/PNG) and size (≤ 10MB)
  → Saves file to /app/uploads (Docker volume)
  → Creates Document record (status: PENDING)
  → Records UPLOAD audit log
  → Dispatches async processing task
      │
      ▼
DocumentProcessingService.processDocument()
  → Status: PROCESSING
  → If PDF:  PDFBox text extraction → Groq LLM structuring
  → If image: Tesseract OCR → Groq LLM structuring
  → Parses JSON response → creates Invoice + LineItems
  → Runs validation (math/tax checks)
  → Runs duplicate detection
  → Status: COMPLETED (or FAILED on error)
  → Records PROCESS_COMPLETE / PROCESS_FAILED audit log
```

### Export Flow

Invoices can be exported as:
- **CSV** — ready for Excel, Google Sheets, or accounting software
- **JSON** — machine-readable structured data

Supported scopes:
- All invoices for the organisation
- A single invoice (with full line items)
- A selected batch (by UUID list)

---

## Configuration

### Key Application Properties

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `server.port` | `SERVER_PORT` | `8081` | API port |
| `spring.datasource.url` | `DB_URL` | — | JDBC connection string |
| `scanly.jwt.secret` | `SCANLY_JWT_SECRET` | — | JWT signing key (base64) |
| `scanly.jwt.expiration-ms` | `SCANLY_JWT_EXPIRATION_MS` | `86400000` | Token lifetime (ms) |
| `groq.api.key` | `GROQ_API_KEY` | — | Groq API key |
| `groq.model` | `GROQ_MODEL` | `openai/gpt-oss-120b` | LLM model name |

### Frontend Proxy (Vite)

The frontend Vite dev server proxies `/api` to the backend automatically — no cross-origin issues during development.

---

## Database

Scanly uses **PostgreSQL 16**. The schema is auto-managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`).

### Core Tables

| Table | Description |
|-------|-------------|
| `organizations` | Multi-tenant root entity |
| `users` | Authenticated users, scoped to an organisation |
| `documents` | Uploaded files with processing status |
| `invoices` | Structured invoice data extracted from documents |
| `line_items` | Individual line items for each invoice |
| `audit_logs` | Append-only log of all system events |

### Indexes

All tables have indexes on foreign keys, status columns, and date columns for efficient pagination and filtering.

---

## Security

- **JWT Authentication:** Stateless tokens signed with HS256. Tokens expire after 24 hours (configurable).
- **Organisation Isolation:** Every query is scoped to the current user's organisation. Users cannot access another org's data even with a valid token.
- **Password Hashing:** BCrypt with Spring Security's default strength factor.
- **File Validation:** Only PDF, JPEG, and PNG files under 10MB are accepted. Files are stored outside the web root.
- **CORS:** Configured to allow requests only from the Vite dev server in development.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes and ensure the backend compiles: `./mvnw package -DskipTests`
4. Test the full stack with Docker: `docker compose up --build -d`
5. Commit and push: `git commit -m "feat: add my feature"`
6. Open a Pull Request

---

## License

This project is developed as a final year academic project. All rights reserved.
