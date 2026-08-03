# 📄 Intelligent Unstructured-to-Structured Data Aggregator

> Automated Invoice/Document Parser & Audit System

---

## 1. Project Summary

This system is an **enterprise-grade data pipeline** that ingests messy, unpredictable files (PDF invoices, scanned images, raw text documents) and outputs strict, validated JSON stored in a structured relational database — with full human audit capabilities.

It is **NOT** a simple AI API wrapper. It is a production-ready, asynchronous document processing system that bridges the gap between unpredictable AI outputs and strict relational business databases.

---

## 2. The Problem

Businesses worldwide waste thousands of hours and millions of dollars manually reviewing **unstructured documents** (PDF invoices, scanned receipts, contracts) and typing that information into their database or ERP systems.

Naively sending documents to a basic AI API introduces three critical engineering problems:

### Problem 1: The "Frozen Screen" (Synchronous Bottleneck)

AI models take 3–10+ seconds to process a single document. If the server waits for the AI to finish before responding to the user's HTTP request, the frontend will freeze. Uploading a batch of 50 invoices would be catastrophic — requests would time out, retry, and potentially crash the server.

### Problem 2: The "Messy Output" (Unpredictable AI Responses)

Large Language Models (LLMs) are conversational by nature. When asked for invoice data, an LLM might respond:

```
Sure! Here is the data you requested:
{ "vendor": "ACME Corp", ... }
Hope that helps! Let me know if you need anything else.
```

The surrounding conversational text **breaks backend JSON parsers**, causing runtime exceptions and data loss.

### Problem 3: The "Trust" Problem (AI Hallucinations)

AI is not 100% accurate. A model might misread `$10,000.00` as `$1,000.00` on a scanned invoice. Blindly inserting AI-parsed financial data into a production database without human oversight is a **compliance and financial risk**.

---

## 3. The Solution

This project solves all three problems using modern software engineering patterns:

| Problem | Pattern | Implementation |
|---|---|---|
| Frozen Screen | **Asynchronous Processing** | Spring Boot + Redis/RabbitMQ background workers |
| Messy Output | **Structured Output Parsing** | Spring AI Structured Output with strict JSON Schema |
| Trust | **Human-in-the-Loop Audit** | React side-by-side dashboard + audit logging |

### How It Works (End-to-End Flow)

1. **User uploads** a PDF invoice or image via the React frontend.
2. **Spring Boot API** immediately saves the file, returns `202 Accepted` with a `job_id`, and pushes a task to the message queue.
3. **Background Worker** picks up the task:
   - Extracts text using OCR (Tesseract) or text extraction (Apache Tika).
   - Sends the extracted text to an AI model (Gemini / OpenAI) with a strict JSON schema prompt.
   - Validates the AI's structured JSON output against business rules.
   - Persists the validated data into PostgreSQL.
4. **Audit Dashboard** displays the original document side-by-side with the extracted fields. Humans can review, correct, and approve the data.
5. **Audit Logs** record every human edit (who changed it, old value, new value, timestamp) for compliance.

---

## 4. Key Goals

- **G1:** Build a non-blocking, asynchronous file processing pipeline.
- **G2:** Achieve reliable structured data extraction from unstructured documents using AI.
- **G3:** Provide a human audit interface for verification and correction.
- **G4:** Maintain a complete audit trail of all data modifications.
- **G5:** Design a clean, normalized relational database schema.
- **G6:** Demonstrate production-readiness with error handling, retries, and observability.

---

## 5. Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Frontend** | React / Next.js | File upload UI, Audit Dashboard |
| **Backend API** | Spring Boot 3.x (Java 21) | REST API, file management, orchestration |
| **Message Queue** | RabbitMQ (or Redis Streams) | Asynchronous task processing |
| **OCR / Text** | Apache Tika + Tesseract OCR | Extract text from PDFs and scanned images |
| **AI Parsing** | Spring AI + Google Gemini API | Structured data extraction with JSON schema |
| **Database** | PostgreSQL 16 | Persistent storage for all entities |
| **File Storage** | Local Disk / AWS S3 | Store uploaded document files |
| **Auth** | Spring Security + JWT | User authentication and authorization |
| **Containerization** | Docker + Docker Compose | Development and deployment environment |

---

## 6. Target Users

| User Role | Description |
|---|---|
| **Accountants / Finance Teams** | Upload invoices and verify extracted data |
| **Data Entry Operators** | Review AI-parsed documents and correct errors |
| **Auditors / Compliance Officers** | Review audit logs for data integrity |
| **System Administrators** | Manage users, monitor pipeline health |

---

## 7. Document Map

| Document | Description |
|---|---|
| [01-PROJECT-OVERVIEW.md](./01-PROJECT-OVERVIEW.md) | This file — high-level problem, solution, and goals |
| [02-ARCHITECTURE.md](./02-ARCHITECTURE.md) | System architecture, data flow, and component diagrams |
| [03-DATABASE-SCHEMA.md](./03-DATABASE-SCHEMA.md) | Full PostgreSQL schema with ER diagram |
| [04-API-SPECIFICATION.md](./04-API-SPECIFICATION.md) | REST API endpoints, request/response contracts |
| [05-ASYNC-PIPELINE.md](./05-ASYNC-PIPELINE.md) | Asynchronous processing, queues, and worker design |
| [06-AI-PARSING-ENGINE.md](./06-AI-PARSING-ENGINE.md) | AI structured output, prompting strategy, validation |
| [07-FRONTEND-DESIGN.md](./07-FRONTEND-DESIGN.md) | React frontend architecture and UI specifications |
| [08-SETUP-GUIDE.md](./08-SETUP-GUIDE.md) | Local development setup and Docker Compose instructions |
| [09-TESTING-STRATEGY.md](./09-TESTING-STRATEGY.md) | Unit, integration, and end-to-end testing plan |
| [10-DEPLOYMENT.md](./10-DEPLOYMENT.md) | Production deployment guide |
| [11-GLOSSARY.md](./11-GLOSSARY.md) | Technical terms and abbreviations |
