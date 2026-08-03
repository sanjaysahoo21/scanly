# 🎨 Frontend Design & Architecture

> React / Next.js with modern UI components

---

## 1. Frontend Architecture

```
src/
├── app/                        # Next.js App Router pages
│   ├── layout.tsx              # Root layout with navigation
│   ├── page.tsx                # Landing / Dashboard page
│   ├── login/
│   │   └── page.tsx            # Login page
│   ├── register/
│   │   └── page.tsx            # Registration page
│   ├── documents/
│   │   ├── page.tsx            # Document list view
│   │   ├── upload/
│   │   │   └── page.tsx        # File upload page
│   │   └── [id]/
│   │       └── page.tsx        # Single document detail + audit view
│   ├── invoices/
│   │   ├── page.tsx            # Invoice list view
│   │   └── [id]/
│   │       └── page.tsx        # Invoice detail + edit view
│   └── audit-logs/
│       └── page.tsx            # Audit log history
│
├── components/
│   ├── layout/
│   │   ├── Navbar.tsx          # Top navigation bar
│   │   ├── Sidebar.tsx         # Side navigation
│   │   └── Footer.tsx          # Footer
│   ├── documents/
│   │   ├── FileUploader.tsx    # Drag-and-drop file upload component
│   │   ├── DocumentCard.tsx    # Document summary card
│   │   ├── DocumentTable.tsx   # Document list table
│   │   └── StatusBadge.tsx     # Processing status indicator
│   ├── invoices/
│   │   ├── InvoiceForm.tsx     # Editable invoice form
│   │   ├── LineItemsTable.tsx  # Editable line items table
│   │   └── InvoiceCard.tsx     # Invoice summary card
│   ├── audit/
│   │   ├── AuditViewer.tsx     # Side-by-side PDF + form view
│   │   ├── PdfViewer.tsx       # PDF renderer (left panel)
│   │   └── AuditLogTable.tsx   # Audit history table
│   └── common/
│       ├── Button.tsx
│       ├── Input.tsx
│       ├── Modal.tsx
│       ├── Pagination.tsx
│       ├── Spinner.tsx
│       └── StatsCard.tsx       # Dashboard metric card
│
├── hooks/
│   ├── useAuth.ts              # Authentication context and JWT management
│   ├── useDocuments.ts         # Document CRUD operations
│   ├── useInvoices.ts          # Invoice CRUD operations
│   └── usePolling.ts           # Poll for document processing status
│
├── services/
│   ├── api.ts                  # Axios/Fetch base configuration
│   ├── authService.ts          # Login, register, token refresh
│   ├── documentService.ts      # Document API calls
│   ├── invoiceService.ts       # Invoice API calls
│   └── auditService.ts         # Audit log API calls
│
├── types/
│   ├── document.ts             # Document TypeScript interfaces
│   ├── invoice.ts              # Invoice TypeScript interfaces
│   ├── user.ts                 # User TypeScript interfaces
│   └── api.ts                  # API response types
│
└── utils/
    ├── formatters.ts           # Date, currency, number formatters
    └── validators.ts           # Client-side form validation
```

---

## 2. Page Specifications

### 2.1 Dashboard (`/`)

The main landing page after login. Shows key metrics and recent activity.

```
┌─────────────────────────────────────────────────────────────┐
│  Navbar: [Logo] [Dashboard] [Documents] [Invoices] [Audit] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ Total    │ │ Pending  │ │ Completed│ │ Needs    │       │
│  │ Docs     │ │ Review   │ │ Today    │ │ Review   │       │
│  │  156     │ │    3     │ │    8     │ │    7     │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│                                                             │
│  ┌─────────────────────────────────────────────────┐        │
│  │  Processing Status Chart (Bar/Pie)              │        │
│  │  [COMPLETED: 140] [FAILED: 5] [REVIEW: 7]      │        │
│  └─────────────────────────────────────────────────┘        │
│                                                             │
│  Recent Documents                                           │
│  ┌─────────────────────────────────────────────────┐        │
│  │ invoice_march.pdf  │ COMPLETED │ 0.94 │ 2m ago  │        │
│  │ receipt_scan.jpg   │ PROCESSING│  -   │ 30s ago │        │
│  │ contract_q2.pdf    │ NEEDS_REV │ 0.72 │ 5m ago  │        │
│  └─────────────────────────────────────────────────┘        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Document Upload (`/documents/upload`)

Drag-and-drop file upload interface supporting batch uploads.

```
┌─────────────────────────────────────────────────────────────┐
│  Upload Documents                                           │
│                                                             │
│  ┌─────────────────────────────────────────────────┐        │
│  │                                                 │        │
│  │     ┌──────────┐                                │        │
│  │     │  📄 📷   │                                │        │
│  │     └──────────┘                                │        │
│  │                                                 │        │
│  │   Drag & drop files here                        │        │
│  │   or click to browse                            │        │
│  │                                                 │        │
│  │   Accepted: PDF, JPG, PNG (max 10MB)            │        │
│  └─────────────────────────────────────────────────┘        │
│                                                             │
│  Selected Files:                                            │
│  ┌─────────────────────────────────────────────────┐        │
│  │ ✅ invoice_march_2026.pdf    (245 KB)  [Remove] │        │
│  │ ✅ receipt_scan.jpg          (1.2 MB)  [Remove] │        │
│  │ ❌ large_doc.pdf             (15 MB)   Too large│        │
│  └─────────────────────────────────────────────────┘        │
│                                                             │
│  [Upload 2 Files]                                           │
│                                                             │
│  Upload Progress:                                           │
│  ┌─────────────────────────────────────────────────┐        │
│  │ invoice_march_2026.pdf  ████████████ 100%  ✅   │        │
│  │ receipt_scan.jpg        ██████░░░░░░  55%  ⏳   │        │
│  └─────────────────────────────────────────────────┘        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Audit Viewer (`/documents/[id]`)

The centerpiece of the frontend — a side-by-side view for human audit.

```
┌─────────────────────────────────────────────────────────────────┐
│  Audit Document: invoice_march_2026.pdf                         │
│  Status: NEEDS_REVIEW  │  Confidence: 0.72  │  [Reprocess]     │
├──────────────────────────────┬──────────────────────────────────┤
│                              │                                  │
│  Original Document           │  Extracted Data (Editable)       │
│                              │                                  │
│  ┌────────────────────────┐  │  Invoice Number: [INV-2026-0891]│
│  │                        │  │  Vendor Name:    [ACME Supplies] │
│  │   [PDF Viewer]         │  │  Vendor GSTIN:   [29AABCU9603R] │
│  │                        │  │  Invoice Date:   [2026-03-15]   │
│  │   Rendered PDF with    │  │  Due Date:       [2026-04-14]   │
│  │   zoom, scroll, and   │  │                                  │
│  │   page navigation     │  │  Subtotal:       [₹12,500.00]   │
│  │                        │  │  Tax:            [₹2,250.00]    │
│  │                        │  │  Discount:       [₹250.00]      │
│  │                        │  │  Total:          [₹14,500.50]   │
│  │                        │  │  ⚠️ Total mismatch detected     │
│  │                        │  │                                  │
│  │                        │  │  Line Items:                     │
│  │                        │  │  ┌────────────────────────────┐  │
│  │                        │  │  │ Server Rack │ 1 │ ₹12,500 │  │
│  │                        │  │  │ [+ Add Item]               │  │
│  │                        │  │  └────────────────────────────┘  │
│  │                        │  │                                  │
│  └────────────────────────┘  │  [Save Changes] [Approve ✅]    │
│                              │                                  │
├──────────────────────────────┴──────────────────────────────────┤
│  Audit History                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ John Doe │ vendor_name │ "ACME Suppl" → "ACME Supplies" │   │
│  │ John Doe │ total_amount│ 14500.00 → 14500.50            │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Components

### 3.1 FileUploader Component

```
Props:
  - maxFiles: number (default: 10)
  - maxSizeBytes: number (default: 10MB)
  - acceptedTypes: string[] (default: ['.pdf', '.jpg', '.jpeg', '.png'])
  - onUploadComplete: (jobs: Job[]) => void

Features:
  - Drag-and-drop zone with visual feedback
  - File type and size validation before upload
  - Upload progress bars
  - Remove files before upload
  - Batch upload support
```

### 3.2 AuditViewer Component

```
Props:
  - documentId: string
  - fileUrl: string
  - invoice: InvoiceData
  - onSave: (updatedInvoice: InvoiceData) => void
  - onApprove: () => void

Features:
  - Split-panel layout (resizable)
  - PDF viewer with zoom and page navigation (left panel)
  - Editable form with field highlighting (right panel)
  - Validation warnings on changed fields
  - Auto-generates audit log entries on save
```

### 3.3 StatusBadge Component

Visual indicator for document processing status.

| Status | Color | Icon |
|---|---|---|
| `PENDING` | Gray | ⏳ Clock |
| `PROCESSING` | Blue | 🔄 Spinner |
| `COMPLETED` | Green | ✅ Check |
| `FAILED` | Red | ❌ Cross |
| `NEEDS_REVIEW` | Amber | ⚠️ Warning |

---

## 4. State Management

Use React's built-in state management:

- **Local State** (`useState`): Form inputs, UI toggles, modals.
- **Server State** (`React Query` / `SWR`): API data fetching, caching, and polling.
- **Auth Context** (`useContext`): Current user, JWT token, organization.

### 4.1 Polling for Processing Status

When a document is uploaded, the frontend needs to check when processing is complete:

```typescript
// usePolling.ts
function useDocumentStatus(documentId: string) {
  return useQuery({
    queryKey: ['document', documentId],
    queryFn: () => documentService.getById(documentId),
    refetchInterval: (data) => {
      // Stop polling when processing is done
      if (data?.status === 'COMPLETED' ||
          data?.status === 'FAILED' ||
          data?.status === 'NEEDS_REVIEW') {
        return false;
      }
      return 3000; // Poll every 3 seconds while processing
    }
  });
}
```

---

## 5. Routing & Navigation

| Route | Page | Auth Required |
|---|---|---|
| `/login` | Login page | No |
| `/register` | Registration page | No |
| `/` | Dashboard | Yes |
| `/documents` | Document list | Yes |
| `/documents/upload` | File upload | Yes |
| `/documents/[id]` | Document detail + audit | Yes |
| `/invoices` | Invoice list | Yes |
| `/invoices/[id]` | Invoice detail + edit | Yes |
| `/audit-logs` | Audit history | Yes (ADMIN/AUDITOR) |

---

## 6. Responsive Design

| Breakpoint | Layout |
|---|---|
| Desktop (≥ 1024px) | Full sidebar + side-by-side audit view |
| Tablet (768–1023px) | Collapsible sidebar, stacked audit panels |
| Mobile (< 768px) | Bottom navigation, single column, tabbed audit view |

The audit viewer is the most critical responsive component — on mobile, the PDF viewer and form become tabs rather than side-by-side panels.
