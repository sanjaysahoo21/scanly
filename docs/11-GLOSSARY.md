# 📖 Glossary

---

| Term | Definition |
|---|---|
| **AI Hallucination** | When an AI model generates plausible but incorrect information (e.g., misreading a number on an invoice). |
| **AMQP** | Advanced Message Queuing Protocol — the protocol used by RabbitMQ for message communication. |
| **Apache Tika** | A Java library for detecting and extracting text from various file formats (PDF, DOCX, etc.). |
| **Asynchronous Processing** | Executing tasks in the background without blocking the main thread or HTTP request. The user gets an immediate response while the work happens later. |
| **Audit Log** | An immutable record of every data change, tracking who changed what, when, and what the old/new values were. |
| **Backpressure** | A mechanism to slow down message producers when consumers can't keep up, preventing system overload. |
| **CQRS** | Command Query Responsibility Segregation — a pattern that separates read and write operations. |
| **Dead Letter Queue (DLQ)** | A special queue where messages are sent when they fail processing after all retry attempts. Used for manual investigation. |
| **DTO** | Data Transfer Object — a plain object used to transfer data between layers (e.g., API request/response bodies). |
| **E2E Test** | End-to-End Test — tests the entire system from the user's perspective, including UI, API, and database. |
| **ERP** | Enterprise Resource Planning — business software that manages day-to-day activities (accounting, procurement, etc.). |
| **Exponential Backoff** | A retry strategy where the wait time between retries increases exponentially (e.g., 2s, 8s, 30s). |
| **GSTIN** | Goods and Services Tax Identification Number — a unique tax identifier for businesses in India. |
| **HSN Code** | Harmonized System of Nomenclature — a standardized code used to classify traded products (used in Indian GST invoices). |
| **Human-in-the-Loop** | A design pattern where AI processes data but a human reviews and approves the output before it becomes final. |
| **Integration Test** | A test that verifies multiple components work together (e.g., API + Database + Queue). |
| **Jitter** | Random variance added to retry delays to prevent multiple retries from hitting the server simultaneously. |
| **JPA** | Java Persistence API — a Java specification for ORM (Object-Relational Mapping). |
| **JSON Schema** | A standard for describing the structure and validation rules of JSON data. |
| **JWT** | JSON Web Token — a compact, URL-safe token used for authentication and authorization. |
| **LLM** | Large Language Model — an AI model trained on large datasets (e.g., GPT-4, Gemini, Claude). |
| **Message Queue** | A middleware system that allows services to communicate asynchronously by sending messages through queues. |
| **Multipart Upload** | An HTTP method for uploading files where the request body is split into multiple parts. |
| **OCR** | Optical Character Recognition — technology that converts images of text into machine-readable text. |
| **ORM** | Object-Relational Mapping — a technique that maps database tables to programming language objects. |
| **Prefetch Count** | The number of messages a RabbitMQ consumer pulls from the queue at once before acknowledging. |
| **RabbitMQ** | An open-source message broker that implements AMQP for asynchronous communication between services. |
| **REST API** | Representational State Transfer API — a web API design pattern using HTTP methods (GET, POST, PUT, DELETE). |
| **Semaphore** | A concurrency mechanism that limits the number of threads accessing a resource simultaneously. |
| **Spring AI** | A Spring Framework module that provides abstractions for working with AI models (Gemini, OpenAI, etc.). |
| **Spring Boot** | A Java framework for building production-ready web applications with minimal configuration. |
| **Spring Security** | A module that provides authentication, authorization, and security features for Spring applications. |
| **Structured Output** | A technique that forces an LLM to return data in a specific format (JSON Schema) rather than free-form text. |
| **Testcontainers** | A Java library that provides lightweight, throwaway instances of databases and services for integration testing. |
| **Tesseract** | An open-source OCR engine developed by Google, used to extract text from images. |
| **Token Bucket** | A rate-limiting algorithm that allows a burst of requests up to a limit, then throttles to a steady rate. |
| **UUID** | Universally Unique Identifier — a 128-bit identifier used as primary keys (e.g., `a1b2c3d4-e5f6-7890-abcd-ef1234567890`). |
| **WebSocket / SSE** | Technologies for real-time server-to-client communication. WebSocket is bidirectional; SSE (Server-Sent Events) is server-to-client only. |
