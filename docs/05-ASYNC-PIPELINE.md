# ⚡ Asynchronous Processing Pipeline

---

## 1. Why Asynchronous?

Document parsing involves two inherently slow operations:

| Operation | Typical Latency |
|---|---|
| OCR / Text extraction from a PDF | 1–5 seconds |
| LLM API call (Gemini / OpenAI) | 2–10 seconds |

If these run inside the HTTP request-response cycle (synchronously), the frontend will freeze for 3–15 seconds per document. Uploading a batch of 50 invoices would take **12+ minutes** of blocked UI.

**The async pattern solves this:**

```
Synchronous (❌ Bad):
  User Upload → [Wait 10s for AI] → Response
  Total: 10 seconds of frozen UI per document

Asynchronous (✅ Good):
  User Upload → 202 Accepted (instant) → [Background worker processes] → User polls for result
  Total: ~200ms for the user, processing happens in the background
```

---

## 2. Queue Architecture

### 2.1 RabbitMQ Setup

```
Exchange: document.exchange (Direct)
   │
   ├──► Queue: document.parse.queue
   │      └── Binding Key: document.parse
   │      └── Consumer: DocumentParserWorker
   │      └── Prefetch: 5 (process 5 at a time)
   │
   └──► Queue: document.parse.dlq (Dead Letter Queue)
          └── Binding Key: document.parse.dlq
          └── Messages that failed after all retries
```

### 2.2 Message Format

When the API receives a file upload, it publishes this message to the queue:

```json
{
  "document_id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
  "file_path": "/uploads/d1e2f3a4/invoice_march_2026.pdf",
  "file_type": "PDF",
  "organization_id": "org-uuid-here",
  "uploaded_by": "user-uuid-here",
  "attempt": 1,
  "published_at": "2026-08-03T10:30:00Z"
}
```

---

## 3. Worker Processing Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                 DocumentParserWorker                              │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ Step 1: Update Status                                   │     │
│  │   UPDATE documents SET status = 'PROCESSING'            │     │
│  │   WHERE id = :document_id                               │     │
│  └─────────────────────────┬───────────────────────────────┘     │
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ Step 2: Extract Text                                    │     │
│  │   IF file_type == 'PDF':                                │     │
│  │     text = ApacheTika.parse(file_path)                  │     │
│  │     IF text is empty or too short:                      │     │
│  │       text = TesseractOCR.ocr(file_path)  // scanned   │     │
│  │   ELSE IF file_type == 'IMAGE':                         │     │
│  │     text = TesseractOCR.ocr(file_path)                  │     │
│  │   SAVE raw_extracted_text to documents table            │     │
│  └─────────────────────────┬───────────────────────────────┘     │
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ Step 3: AI Structured Parsing                           │     │
│  │   prompt = buildPrompt(text, JSON_SCHEMA)               │     │
│  │   response = springAI.call(prompt)                      │     │
│  │   parsedInvoice = parseStructuredOutput(response)       │     │
│  │   SAVE ai_raw_response (JSONB) to documents table       │     │
│  └─────────────────────────┬───────────────────────────────┘     │
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ Step 4: Validate & Apply Business Rules                 │     │
│  │   - Are required fields present?                        │     │
│  │   - Is invoice_date a valid date?                       │     │
│  │   - Are amounts valid numbers ≥ 0?                      │     │
│  │   - Does SUM(line_items.total_price) ≈ subtotal?        │     │
│  │   - Does subtotal + tax - discount ≈ total_amount?      │     │
│  │   - Compute confidence_score based on validation pass   │     │
│  └─────────────────────────┬───────────────────────────────┘     │
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │ Step 5: Persist & Update Status                         │     │
│  │   INSERT INTO invoices (...) VALUES (...)               │     │
│  │   INSERT INTO line_items (...) VALUES (...)             │     │
│  │   IF confidence_score >= 0.85:                          │     │
│  │     UPDATE documents SET status = 'COMPLETED'           │     │
│  │   ELSE:                                                 │     │
│  │     UPDATE documents SET status = 'NEEDS_REVIEW'        │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Retry Strategy

Not all failures are permanent. Network timeouts, AI API rate limits, and transient errors should be retried.

### 4.1 Retry Policy

| Parameter | Value |
|---|---|
| Max Retries | 3 |
| Backoff Strategy | Exponential with jitter |
| Initial Delay | 2 seconds |
| Max Delay | 30 seconds |
| Retryable Errors | Network timeout, 429 (rate limit), 500/503 from AI API |
| Non-Retryable Errors | 400 (bad request), file corrupted, invalid file type |

### 4.2 Retry Flow

```
Attempt 1 (immediate)
   └── FAIL → wait 2s (+jitter)
Attempt 2
   └── FAIL → wait 8s (+jitter)
Attempt 3
   └── FAIL → wait 30s (+jitter)
Attempt 4
   └── FAIL → Move to Dead Letter Queue
             → UPDATE documents SET status = 'FAILED',
               error_message = 'Max retries exceeded: <reason>'
```

### 4.3 Dead Letter Queue (DLQ)

Messages that fail after all retries are moved to `document.parse.dlq`. These represent documents that need manual investigation.

**Handling DLQ messages:**
- An admin can view failed documents in the dashboard.
- The admin can fix the issue (e.g., re-upload a clearer scan) and click "Reprocess."
- Reprocessing publishes a new message to the main queue with `attempt` reset to 1.

---

## 5. Scaling Considerations

### 5.1 Horizontal Worker Scaling

Multiple worker instances can consume from the same RabbitMQ queue. RabbitMQ distributes messages round-robin across consumers.

```
                    ┌── Worker Instance 1
                    │
Queue ──────────────┼── Worker Instance 2
                    │
                    └── Worker Instance 3
```

### 5.2 Prefetch / Concurrency

Each worker instance should set a **prefetch count** to control how many messages it processes concurrently:

```java
@RabbitListener(queues = "document.parse.queue",
                concurrency = "3-5")
public void processDocument(DocumentParseMessage message) {
    // ...
}
```

- `concurrency = "3-5"`: Spring creates 3 consumer threads, scaling up to 5 under load.
- Each consumer processes one message at a time (per thread).

### 5.3 Rate Limiting for AI API

AI APIs (Gemini, OpenAI) have rate limits (e.g., 60 requests/minute). Use a **token bucket** or **semaphore** to throttle AI API calls:

```java
private final Semaphore aiApiSemaphore = new Semaphore(10); // max 10 concurrent AI calls

public InvoiceData callAiApi(String text) {
    aiApiSemaphore.acquire();
    try {
        return springAiClient.parse(text);
    } finally {
        aiApiSemaphore.release();
    }
}
```

---

## 6. Monitoring & Observability

### 6.1 Key Metrics to Track

| Metric | Description |
|---|---|
| `documents.uploaded.count` | Total documents uploaded |
| `documents.processed.count` | Total documents successfully processed |
| `documents.failed.count` | Total documents that failed |
| `documents.processing.duration` | Time taken to process each document |
| `queue.depth` | Number of messages waiting in the queue |
| `queue.dlq.depth` | Number of messages in the dead letter queue |
| `ai.api.latency` | Response time of AI API calls |
| `ai.api.errors` | Count of AI API errors |

### 6.2 Health Checks

The Spring Boot application should expose health checks for:
- **Database connectivity** (`/actuator/health/db`)
- **RabbitMQ connectivity** (`/actuator/health/rabbit`)
- **Disk space** (`/actuator/health/diskSpace`)
