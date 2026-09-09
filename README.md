# 📄 Scanly — Intelligent Document Parser & Audit System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![React](https://img.shields.io/badge/React-19.2-61DAFB?style=flat&logo=react&logoColor=black)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-8.2-646CFF?style=flat&logo=vite&logoColor=white)](https://vitejs.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Groq AI](https://img.shields.io/badge/AI%20Engine-Groq%20LLM-F55036?style=flat)](https://groq.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)](https://www.docker.com/)

---

## 📌 Overview

**Scanly** is an enterprise-grade document intelligence and auditing platform designed to convert unstructured financial documents (PDF invoices, receipts, and billing records) into strictly validated, queryable relational data.

Unlike basic AI wrappers, Scanly provides an **asynchronous processing pipeline**, **local text extraction with Apache PDFBox**, **structured Groq AI LLM inference**, an **in-browser PDF previewer with side-by-side editing**, and an **immutable audit log** for human-in-the-loop review and compliance.

---

## ✨ Key Features

- **⚡ Asynchronous Processing Pipeline**: Non-blocking file uploads returning instant acknowledgment while background workers process OCR, extraction, and validation.
- **🧠 AI-Powered Structured Extraction**: Combines Apache PDFBox text extraction with Groq's high-speed LLM inference to reliably extract invoice metadata, buyer/vendor GSTINs, dates, tax amounts, and granular line items into strict JSON.
- **👁️ Side-by-Side Audit Studio**: View uploaded PDF documents in an authenticated in-browser preview tab alongside an interactive edit form for live correction and validation.
- **📊 Confidence Scoring & Flagging**: Automatically calculates extraction confidence scores. Invoices with low confidence or missing essential fields are flagged as `NEEDS_REVIEW` for manual verification.
- **🛡️ Immutable Audit Trail**: Records every modification, approval, and reprocessing action with timestamps, user identities, field names, and before/after values.
- **📈 Real-Time Dashboard**: High-level metrics tracking total uploads, processed documents, pending review counts, total spend, and audit history.
- **🔐 JWT Authentication & Multi-Tenancy**: Secure stateless authentication using JSON Web Tokens (JWT) with role-based access control (`ADMIN`, `AUDITOR`, `OPERATOR`) scoped to organizations.
- **🌱 Environment-Driven Configuration**: Fully synchronized with `.env` files and Docker Compose without hardcoded credentials or push protection risks.

---

## 🏗️ System Architecture

```
[ Frontend (React 19 + Vite) ]
         │  HTTP / REST (Proxy: /api/v1 -> :8081)
         ▼
[ Spring Boot 4 Backend API ]
   ├── Spring Security (JWT Filter & RBAC)
   ├── Controllers (Documents, Invoices, Audits, Dashboard, Auth)
   ├── Async Service Layer (@Async DocumentProcessingService)
   │     ├── Apache PDFBox (Local PDF Text Stripping)
   │     └── Groq AI Service (Structured JSON Extraction)
   └── Spring Data JPA / Hibernate
         │  JDBC (Port 5433)
         ▼
[ PostgreSQL 16 Database (Docker) ]
   ├── users & organizations
   ├── documents (metadata & raw extraction)
   ├── invoices & line_items
   └── audit_logs
```

---

## 🛠️ Technology Stack

### Backend
- **Language & Framework**: Java 21, Spring Boot 4.1.0
- **Security**: Spring Security 7, JJWT (io.jsonwebtoken 0.12.6), BCrypt
- **Persistence**: Spring Data JPA, Hibernate ORM 7, HikariCP
- **Database Driver**: PostgreSQL JDBC Driver
- **Text & PDF Extraction**: Apache PDFBox 3.0.3
- **AI / LLM Provider**: Groq API (`openai/gpt-oss-120b` or custom OpenAI-compatible endpoints)
- **Environment Management**: `dotenv-java` 3.1.0

### Frontend
- **Framework & Build Tool**: React 19, Vite 8
- **Routing**: React Router v7
- **Icons**: Lucide React
- **Styling**: Modern Vanilla CSS Design System with responsive grid, glassmorphism, and status badges

### Infrastructure & DevOps
- **Containerization**: Docker & Docker Compose
- **Database**: PostgreSQL 16 Alpine
- **Build System**: Maven (via `./mvnw` wrapper)

---

## 📁 Repository Structure

```
final-year-project/
├── .env.example               # Template for environment variables
├── docker-compose.yml         # PostgreSQL 16 service definition
├── docs/                      # Architectural specs & design guides
│   ├── 01-PROJECT-OVERVIEW.md
│   ├── 02-ARCHITECTURE.md
│   ├── 03-DATABASE-SCHEMA.md
│   ├── 04-API-SPECIFICATION.md
│   ├── 05-ASYNC-PIPELINE.md
│   ├── 06-AI-PARSING-ENGINE.md
│   └── ...
├── backend/                   # Spring Boot 4 REST API
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/main/java/com/scanly/backend/
│       ├── config/            # Security, Async, CORS configuration
│       ├── controller/        # REST Endpoints (Auth, Doc, Invoice, Audit, Dash)
│       ├── dto/               # Request/Response data transfer objects
│       ├── entity/            # JPA Entities (Document, Invoice, LineItem, etc.)
│       ├── repository/        # Spring Data JPA Repositories
│       ├── security/          # JWT utilities & filters
│       └── service/           # Business logic & Groq AI processing
└── frontend/                  # React 19 Single Page Application
    ├── package.json
    ├── vite.config.js         # Vite dev server & backend proxy
    └── src/
        ├── components/        # Reusable UI, tables, audit viewer, invoice form
        ├── pages/             # Dashboard, Upload, Invoices, Audit Studio
        ├── services/          # API integration clients (Auth, Document, Invoice)
        └── styles/            # CSS tokens, layout, and component themes
```

---

## 🚀 Quick Start Guide

### 1. Prerequisites
- **Java 21 JDK** installed
- **Node.js** (v18+ or v20+) and **npm**
- **Docker & Docker Compose** installed and running

---

### 2. Environment Configuration

Copy the `.env.example` file to create your `.env` configuration in the project root:

```bash
cp .env.example .env
```

Ensure your `.env` file contains your credentials:

```properties
# Server Configuration
SERVER_PORT=8081

# PostgreSQL Database Configuration
DB_URL=jdbc:postgresql://127.0.0.1:5433/scanly
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password
POSTGRES_DB=scanly
DB_PORT=5433

# JWT Security (Base64-encoded 256-bit string)
SCANLY_JWT_SECRET=c2Nhbmx5LXNlY3JldC1rZXktZm9yLWp3dC10b2tlbi1zaWduaW5nLTIwMjY=
SCANLY_JWT_EXPIRATION_MS=86400000

# AI / OCR Service (Groq API Key from console.groq.com)
GROQ_API_KEY=gsk_your_groq_api_key_here
GROQ_MODEL=openai/gpt-oss-120b
```

---

### 3. Start PostgreSQL Database

Launch the containerized PostgreSQL database using Docker Compose:

```bash
docker compose up -d
```

Verify that the database is running on port `5433`:
```bash
docker compose ps
```

---

### 4. Start the Backend API

From the root directory, navigate to the `backend` folder and start the Spring Boot application using the Maven wrapper:

```bash
cd backend
./mvnw spring-boot:run
```
*(On Windows PowerShell, run `.\mvnw spring-boot:run`)*

The backend will automatically load your `.env` file and start on **`http://localhost:8081`**.

---

### 5. Start the Frontend Application

Open a new terminal, navigate to the `frontend` folder, install dependencies, and start the development server:

```bash
cd frontend
npm install
npm run dev
```

The frontend will start at **`http://localhost:3000`** (or `http://localhost:5173`) and automatically proxy all `/api/*` requests to `http://localhost:8081`.

---

## 📡 REST API Reference

All protected endpoints require the HTTP header:
`Authorization: Bearer <JWT_TOKEN>`

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new user & organization |
| `POST` | `/api/v1/auth/login` | Authenticate user and receive JWT token |

### Documents
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/documents/upload` | Multipart file upload (batch supported) |
| `GET` | `/api/v1/documents` | List all uploaded documents for the organization |
| `GET` | `/api/v1/documents/{id}/detail` | Fetch document metadata, extracted invoice & audit logs |
| `GET` | `/api/v1/documents/{id}/file` | Stream raw PDF for in-browser preview |
| `POST` | `/api/v1/documents/{id}/reprocess` | Trigger re-extraction via Groq AI pipeline |
| `POST` | `/api/v1/documents/{id}/approve` | Mark document invoice as audited & approved |

### Invoices
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/invoices` | List extracted invoices |
| `GET` | `/api/v1/invoices/{id}` | Get single invoice with line items |
| `PUT` | `/api/v1/documents/{id}/invoice` | Update extracted invoice fields & record audit log |

### Audit Logs & Dashboard
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/audit-logs` | Retrieve paginated audit trail logs |
| `GET` | `/api/v1/dashboard/stats` | Retrieve aggregate metrics, total spend & status counts |

---

## 🧪 Testing

To run the backend integration and unit test suite:

```bash
cd backend
./mvnw test
```

---

## 🔒 Security Best Practices

- **Zero Committed Secrets**: All sensitive API keys (`GROQ_API_KEY`, `SCANLY_JWT_SECRET`, database passwords) are stored in `.env`, which is ignored by `.gitignore`.
- **Stateless Session**: Authentication relies entirely on signed JWT tokens; passwords are encrypted with `BCryptPasswordEncoder`.
- **Role-Based Access**: Critical mutations (invoice editing, document approvals, and reprocessing) are guarded by `@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")`.

---

## 📄 License

This project is licensed under the MIT License — feel free to use and adapt it for research and educational purposes.
