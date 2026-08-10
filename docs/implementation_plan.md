# Scanly Backend — Feature-by-Feature Development Plan

> **Approach:** Build one feature at a time. After each feature, you can start the app, test it with Postman/curl, debug it, and fully understand it before we move to the next.

---

## Feature Order

| # | Feature | What You Can Test |
|---|---|---|
| **1** | Project Config + Health Check | Start the app, hit `GET /api/v1/health` → get `{ "status": "UP" }` |
| **2** | Database Entities + JPA | App starts, tables auto-created in PostgreSQL, verify in pgAdmin |
| **3** | Auth — Register + Login (JWT) | Register a user, login, get a JWT token, use it on protected endpoints |
| **4** | Document Upload + File Storage | Upload a PDF/image via `POST /documents/upload`, see it saved to disk |
| **5** | Document CRUD API | List documents, get by ID, download original file, filter by status |
| **6** | Async Pipeline (RabbitMQ + OCR + AI) | Upload → queue → worker extracts text → AI parses → invoice created |
| **7** | Invoice CRUD + Audit Logs | View/edit invoices, approve them, see audit trail of changes |
| **8** | Dashboard Stats | `GET /dashboard/stats` returns aggregated metrics |

---

## Feature 1: Project Config + Health Check

> **Goal:** Get the Spring Boot app running with proper configuration and a simple health check endpoint. Nothing more.

### What We'll Build

#### [MODIFY] [pom.xml](file:///c:/Users/Lenovo/Documents/final-year-project/backend/pom.xml)
- Add missing dependencies: `spring-boot-starter-validation`, `lombok`
- Keep existing: `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-webmvc`, `postgresql`
- **Don't add** RabbitMQ, Spring AI, etc. yet — those come in later features

#### [MODIFY] [application.properties](file:///c:/Users/Lenovo/Documents/final-year-project/backend/src/main/resources/application.properties)
- PostgreSQL connection (URL, username, password)
- JPA/Hibernate settings (`ddl-auto=update` for dev)
- File upload settings (max size: 10MB)
- Server port: 8080
- CORS: Allow `http://localhost:3000` (React frontend)

#### [NEW] `config/CorsConfig.java`
- Enable CORS for the React frontend

#### [NEW] `config/SecurityConfig.java`
- **Temporarily** permit all requests (no JWT yet — that's Feature 3)
- Disable CSRF (we're a REST API)

#### [NEW] `controller/HealthController.java`
- `GET /api/v1/health` → returns `{ "status": "UP", "timestamp": "..." }`

### How You'll Test It
```bash
# Start the app
./mvnw spring-boot:run

# Test health endpoint
curl http://localhost:8080/api/v1/health
# Expected: { "status": "UP", "timestamp": "2026-08-10T..." }
```

---

## Feature 2–8 (Coming After You Test Feature 1)

We'll plan each one in detail right before building it. Here's a quick preview:

- **Feature 2:** JPA entities for all 6 tables (Organization, User, Document, Invoice, LineItem, AuditLog) + repositories
- **Feature 3:** JWT auth — register/login endpoints, token generation/validation, Spring Security filter
- **Feature 4:** File upload endpoint — multipart handling, save to disk, create `PENDING` document record
- **Feature 5:** Document list/detail/download/filter endpoints
- **Feature 6:** RabbitMQ queue → worker → Tika/Tesseract OCR → Gemini AI parsing → save invoice
- **Feature 7:** Invoice CRUD, line items, approve/reject, audit log creation on edits
- **Feature 8:** Dashboard aggregation endpoint with counts and stats

---

## Prerequisites

> [!IMPORTANT]
> Before we start Feature 1, you need **PostgreSQL** running locally. Do you have it installed?
> 
> If not, you can either:
> - Install PostgreSQL directly: [postgresql.org/download](https://www.postgresql.org/download/)
> - Or run it via Docker: `docker run -d --name scanly-db -e POSTGRES_PASSWORD=scanly123 -e POSTGRES_DB=scanly -p 5432:5432 postgres:16`

## Open Questions

> [!NOTE]
> 1. **PostgreSQL credentials:** What username/password/database name do you want to use? (I'll default to `postgres` / `scanly123` / `scanly` if you don't specify)
> 2. **Do you have PostgreSQL installed?** If not, would you prefer Docker or a direct install?
