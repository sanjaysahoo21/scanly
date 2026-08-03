# 🌐 REST API Specification

> Base URL: `http://localhost:8080/api/v1`

---

## 1. Authentication

All endpoints (except `/auth/**`) require a valid JWT token in the `Authorization` header:

```
Authorization: Bearer <jwt_token>
```

---

## 2. Authentication Endpoints

### POST `/auth/register`

Register a new user account.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "full_name": "John Doe",
  "organization_name": "ACME Corp"
}
```

**Response: `201 Created`**
```json
{
  "user_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "user@example.com",
  "token": "eyJhbGciOiJIUzI1NiIs..."
}
```

### POST `/auth/login`

Authenticate and receive a JWT token.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response: `200 OK`**
```json
{
  "user_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "user@example.com",
  "role": "ADMIN",
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_at": "2026-08-04T14:00:00Z"
}
```

**Error: `401 Unauthorized`**
```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "Invalid email or password"
}
```

---

## 3. Document Endpoints

### POST `/documents/upload`

Upload one or more documents for processing.

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|---|---|---|---|
| `files` | File[] | Yes | One or more PDF, JPG, or PNG files |

**Response: `202 Accepted`**
```json
{
  "message": "Documents accepted for processing",
  "jobs": [
    {
      "job_id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
      "file_name": "invoice_march_2026.pdf",
      "status": "PENDING"
    },
    {
      "job_id": "e2f3a4b5-c6d7-8901-bcde-f12345678901",
      "file_name": "receipt_scan.jpg",
      "status": "PENDING"
    }
  ]
}
```

**Error: `400 Bad Request`**
```json
{
  "error": "INVALID_FILE_TYPE",
  "message": "Only PDF, JPG, and PNG files are accepted"
}
```

**Error: `413 Payload Too Large`**
```json
{
  "error": "FILE_TOO_LARGE",
  "message": "File size exceeds 10MB limit"
}
```

### GET `/documents`

List all documents for the authenticated user's organization.

**Query Parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `status` | String | (all) | Filter by status: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `NEEDS_REVIEW` |
| `page` | Integer | 0 | Page number (0-indexed) |
| `size` | Integer | 20 | Page size (max 100) |
| `sort` | String | `created_at,desc` | Sort field and direction |

**Response: `200 OK`**
```json
{
  "content": [
    {
      "id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
      "file_name": "invoice_march_2026.pdf",
      "file_type": "PDF",
      "file_size_bytes": 245120,
      "status": "COMPLETED",
      "confidence_score": 0.94,
      "uploaded_by": "John Doe",
      "created_at": "2026-08-03T10:30:00Z",
      "updated_at": "2026-08-03T10:30:45Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1
}
```

### GET `/documents/{id}`

Get a single document with its processing status and extracted data.

**Response: `200 OK`**
```json
{
  "id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
  "file_name": "invoice_march_2026.pdf",
  "file_type": "PDF",
  "file_size_bytes": 245120,
  "status": "COMPLETED",
  "confidence_score": 0.94,
  "error_message": null,
  "raw_extracted_text": "INVOICE\nINV-2026-0891\nACME Supplies...",
  "uploaded_by": "John Doe",
  "created_at": "2026-08-03T10:30:00Z",
  "invoice": {
    "id": "f3a4b5c6-d7e8-9012-cdef-123456789012",
    "invoice_number": "INV-2026-0891",
    "vendor_name": "ACME Supplies Pvt Ltd",
    "total_amount": 14500.50,
    "is_audited": false
  }
}
```

### GET `/documents/{id}/file`

Download the original uploaded file.

**Response: `200 OK`** — Binary file stream with appropriate `Content-Type` header.

### POST `/documents/{id}/reprocess`

Resubmit a failed or inaccurate document for re-parsing.

**Response: `202 Accepted`**
```json
{
  "job_id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
  "status": "PENDING",
  "message": "Document re-queued for processing"
}
```

---

## 4. Invoice Endpoints

### GET `/invoices`

List all invoices for the authenticated user's organization.

**Query Parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `is_audited` | Boolean | (all) | Filter by audit status |
| `vendor_name` | String | (all) | Search by vendor name (partial match) |
| `date_from` | Date | (all) | Invoice date range start |
| `date_to` | Date | (all) | Invoice date range end |
| `page` | Integer | 0 | Page number |
| `size` | Integer | 20 | Page size |

**Response: `200 OK`**
```json
{
  "content": [
    {
      "id": "f3a4b5c6-d7e8-9012-cdef-123456789012",
      "document_id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
      "invoice_number": "INV-2026-0891",
      "vendor_name": "ACME Supplies Pvt Ltd",
      "invoice_date": "2026-03-15",
      "total_amount": 14500.50,
      "currency": "INR",
      "is_audited": false,
      "created_at": "2026-08-03T10:30:45Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1
}
```

### GET `/invoices/{id}`

Get a single invoice with all line items.

**Response: `200 OK`**
```json
{
  "id": "f3a4b5c6-d7e8-9012-cdef-123456789012",
  "document_id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
  "invoice_number": "INV-2026-0891",
  "vendor_name": "ACME Supplies Pvt Ltd",
  "vendor_address": "123 Industrial Area, Bangalore 560001",
  "vendor_gstin": "29AABCU9603R1ZP",
  "buyer_name": "TechStart Innovations",
  "buyer_address": "456 MG Road, Mumbai 400001",
  "buyer_gstin": "27AADCB2230M1Z2",
  "invoice_date": "2026-03-15",
  "due_date": "2026-04-14",
  "subtotal": 12500.00,
  "tax_amount": 2250.00,
  "discount_amount": 250.00,
  "total_amount": 14500.50,
  "currency": "INR",
  "is_audited": false,
  "audited_by": null,
  "audited_at": null,
  "line_items": [
    {
      "id": "a4b5c6d7-e8f9-0123-defg-234567890123",
      "description": "Server Rack Cabinet 42U",
      "hsn_code": "73089090",
      "quantity": 1,
      "unit_price": 12500.00,
      "tax_rate": 18.00,
      "total_price": 14750.00
    }
  ],
  "created_at": "2026-08-03T10:30:45Z",
  "updated_at": "2026-08-03T10:30:45Z"
}
```

### PUT `/invoices/{id}`

Update an invoice (human audit correction). Automatically creates audit log entries for each changed field.

**Request Body:**
```json
{
  "vendor_name": "ACME Supplies Private Limited",
  "total_amount": 14550.50,
  "line_items": [
    {
      "id": "a4b5c6d7-e8f9-0123-defg-234567890123",
      "description": "Server Rack Cabinet 42U (Updated)",
      "quantity": 1,
      "unit_price": 12550.00,
      "tax_rate": 18.00,
      "total_price": 14809.00
    }
  ]
}
```

**Response: `200 OK`**
```json
{
  "id": "f3a4b5c6-d7e8-9012-cdef-123456789012",
  "message": "Invoice updated successfully",
  "audit_entries_created": 3
}
```

### POST `/invoices/{id}/approve`

Mark an invoice as audited and approved.

**Response: `200 OK`**
```json
{
  "id": "f3a4b5c6-d7e8-9012-cdef-123456789012",
  "is_audited": true,
  "audited_by": "John Doe",
  "audited_at": "2026-08-03T12:00:00Z"
}
```

---

## 5. Audit Log Endpoints

### GET `/audit-logs`

List all audit log entries with filtering.

**Query Parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `document_id` | UUID | (all) | Filter by document |
| `invoice_id` | UUID | (all) | Filter by invoice |
| `user_id` | UUID | (all) | Filter by user |
| `action` | String | (all) | Filter by action: `EDIT`, `APPROVE`, `REJECT` |
| `date_from` | DateTime | (all) | Created at range start |
| `date_to` | DateTime | (all) | Created at range end |
| `page` | Integer | 0 | Page number |
| `size` | Integer | 50 | Page size |

**Response: `200 OK`**
```json
{
  "content": [
    {
      "id": "b5c6d7e8-f901-2345-efgh-345678901234",
      "document_id": "d1e2f3a4-b5c6-7890-abcd-ef1234567890",
      "invoice_id": "f3a4b5c6-d7e8-9012-cdef-123456789012",
      "user": "John Doe",
      "entity_type": "INVOICE",
      "field_name": "vendor_name",
      "old_value": "ACME Supplies Pvt Ltd",
      "new_value": "ACME Supplies Private Limited",
      "action": "EDIT",
      "created_at": "2026-08-03T11:45:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "total_elements": 1,
  "total_pages": 1
}
```

---

## 6. Dashboard / Analytics Endpoints

### GET `/dashboard/stats`

Get summary statistics for the organization.

**Response: `200 OK`**
```json
{
  "total_documents": 156,
  "documents_by_status": {
    "PENDING": 3,
    "PROCESSING": 1,
    "COMPLETED": 140,
    "FAILED": 5,
    "NEEDS_REVIEW": 7
  },
  "total_invoices": 140,
  "audited_invoices": 128,
  "unaudited_invoices": 12,
  "average_confidence_score": 0.91,
  "total_invoice_value": 4235600.75,
  "documents_today": 8,
  "documents_this_week": 42
}
```

---

## 7. Common Error Response Format

All errors follow a consistent structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error description",
  "timestamp": "2026-08-03T12:00:00Z",
  "path": "/api/v1/documents/invalid-id"
}
```

### Standard HTTP Status Codes Used

| Code | Meaning | When Used |
|---|---|---|
| `200` | OK | Successful read/update |
| `201` | Created | Successful resource creation |
| `202` | Accepted | Async task accepted for processing |
| `400` | Bad Request | Invalid input, validation failure |
| `401` | Unauthorized | Missing or invalid JWT |
| `403` | Forbidden | Insufficient permissions |
| `404` | Not Found | Resource does not exist |
| `413` | Payload Too Large | File exceeds size limit |
| `429` | Too Many Requests | Rate limit exceeded |
| `500` | Internal Server Error | Unexpected server failure |
