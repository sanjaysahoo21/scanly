# 🤖 AI Parsing Engine

---

## 1. Overview

The AI Parsing Engine is the core intelligence of the system. It takes raw, unstructured text extracted from documents and converts it into strict, validated JSON that can be reliably stored in a relational database.

**Pipeline:**

```
Raw Document → Text Extraction → AI Structured Parsing → Validation → Structured Data
```

---

## 2. Text Extraction Layer

### 2.1 Decision Tree

```
Is the file a PDF?
├── YES → Try Apache Tika (native text extraction)
│          └── Did Tika extract meaningful text (> 50 chars)?
│              ├── YES → Use Tika output
│              └── NO  → File is likely a scanned PDF → Use Tesseract OCR
│
└── NO → Is the file an image (JPG/PNG)?
         ├── YES → Use Tesseract OCR
         └── NO  → Is it a plain text file?
                   ├── YES → Read directly
                   └── NO  → Return error: UNSUPPORTED_FILE_TYPE
```

### 2.2 Apache Tika

Apache Tika extracts text from native (digitally created) PDFs. These are PDFs where the text is selectable — not scanned images.

```java
import org.apache.tika.Tika;

public String extractTextFromPdf(InputStream inputStream) {
    Tika tika = new Tika();
    tika.setMaxStringLength(100_000); // limit to 100K characters
    return tika.parseToString(inputStream);
}
```

**When Tika fails:** If the extracted text is empty or very short (< 50 characters), the PDF is likely a scanned document and should be routed to Tesseract OCR.

### 2.3 Tesseract OCR

Tesseract is an open-source OCR engine that reads text from images. For scanned PDFs, each page is first converted to an image, then OCR is applied.

```java
import net.sourceforge.tess4j.Tesseract;

public String ocrFromImage(File imageFile) {
    Tesseract tesseract = new Tesseract();
    tesseract.setDatapath("/usr/share/tessdata");
    tesseract.setLanguage("eng");
    tesseract.setPageSegMode(6); // Assume a single uniform block of text
    return tesseract.doOCR(imageFile);
}
```

**For scanned PDFs:** Use a library like Apache PDFBox to render each page as an image, then pass each image through Tesseract:

```java
import org.apache.pdfbox.rendering.PDFRenderer;

PDDocument document = PDDocument.load(pdfFile);
PDFRenderer renderer = new PDFRenderer(document);
StringBuilder fullText = new StringBuilder();

for (int page = 0; page < document.getNumberOfPages(); page++) {
    BufferedImage image = renderer.renderImageWithDPI(page, 300); // 300 DPI for OCR
    String pageText = tesseract.doOCR(image);
    fullText.append(pageText).append("\n");
}
```

---

## 3. AI Structured Output Parsing

### 3.1 The Problem with Raw LLM Output

If you naively ask an LLM to "extract invoice data," it might respond:

```
Sure! I've analyzed the invoice. Here are the extracted details:

The invoice was issued by ACME Supplies Pvt Ltd with number INV-2026-0891
dated March 15, 2026. The total amount is ₹14,500.50 including 18% GST.

Here's the JSON:
{
  "vendor": "ACME Supplies Pvt Ltd",
  "total": "14,500.50"  // ← String, not a number!
}

Let me know if you need anything else!
```

**Problems:**
1. Conversational filler text surrounds the JSON → JSON parser fails.
2. Field names are inconsistent (`vendor` vs `vendor_name`).
3. Data types are wrong (`"14,500.50"` is a string, not a number).
4. Fields are missing (no line items, no tax breakdown).

### 3.2 The Solution: Structured Output with JSON Schema

Use **Spring AI's Structured Output** capability to force the LLM to return data in a strict schema.

#### Target JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["vendor_name", "invoice_number", "total_amount", "line_items"],
  "properties": {
    "invoice_number": {
      "type": "string",
      "description": "The unique invoice identifier (e.g., INV-2026-0891)"
    },
    "vendor_name": {
      "type": "string",
      "description": "The name of the vendor/seller who issued the invoice"
    },
    "vendor_address": {
      "type": ["string", "null"],
      "description": "Full address of the vendor"
    },
    "vendor_gstin": {
      "type": ["string", "null"],
      "description": "GST Identification Number of the vendor (Indian tax ID)"
    },
    "buyer_name": {
      "type": ["string", "null"],
      "description": "The name of the buyer/customer"
    },
    "buyer_address": {
      "type": ["string", "null"],
      "description": "Full address of the buyer"
    },
    "buyer_gstin": {
      "type": ["string", "null"],
      "description": "GST Identification Number of the buyer"
    },
    "invoice_date": {
      "type": ["string", "null"],
      "format": "date",
      "description": "Date the invoice was issued (ISO 8601: YYYY-MM-DD)"
    },
    "due_date": {
      "type": ["string", "null"],
      "format": "date",
      "description": "Payment due date (ISO 8601: YYYY-MM-DD)"
    },
    "subtotal": {
      "type": ["number", "null"],
      "description": "Sum before tax and discounts"
    },
    "tax_amount": {
      "type": ["number", "null"],
      "description": "Total tax amount"
    },
    "discount_amount": {
      "type": ["number", "null"],
      "description": "Total discount applied"
    },
    "total_amount": {
      "type": "number",
      "description": "Final payable amount"
    },
    "currency": {
      "type": "string",
      "enum": ["INR", "USD", "EUR", "GBP"],
      "default": "INR",
      "description": "Currency code"
    },
    "line_items": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["description", "quantity", "unit_price", "total_price"],
        "properties": {
          "description": {
            "type": "string",
            "description": "Item or service description"
          },
          "hsn_code": {
            "type": ["string", "null"],
            "description": "HSN/SAC code for the item"
          },
          "quantity": {
            "type": "number",
            "description": "Number of units"
          },
          "unit_price": {
            "type": "number",
            "description": "Price per unit"
          },
          "tax_rate": {
            "type": ["number", "null"],
            "description": "Tax rate in percentage (e.g., 18.0 for 18%)"
          },
          "total_price": {
            "type": "number",
            "description": "Total price for this line item"
          }
        }
      }
    }
  }
}
```

### 3.3 Spring AI Implementation

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;

// Define the Java record matching the schema
public record InvoiceData(
    String invoiceNumber,
    String vendorName,
    String vendorAddress,
    String vendorGstin,
    String buyerName,
    String buyerAddress,
    String buyerGstin,
    LocalDate invoiceDate,
    LocalDate dueDate,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    String currency,
    List<LineItemData> lineItems
) {}

public record LineItemData(
    String description,
    String hsnCode,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal taxRate,
    BigDecimal totalPrice
) {}

// Service method
public InvoiceData parseInvoice(String extractedText) {
    BeanOutputConverter<InvoiceData> converter =
        new BeanOutputConverter<>(InvoiceData.class);

    String prompt = """
        You are a precise document parsing engine. Extract structured invoice data
        from the following raw text. Return ONLY valid JSON matching the schema.
        Do not include any explanation or commentary.

        If a field is not found in the text, set it to null.
        Dates must be in ISO 8601 format (YYYY-MM-DD).
        Amounts must be plain numbers without currency symbols or commas.

        --- BEGIN DOCUMENT TEXT ---
        %s
        --- END DOCUMENT TEXT ---

        %s
        """.formatted(extractedText, converter.getFormat());

    InvoiceData result = chatClient.prompt()
        .user(prompt)
        .call()
        .entity(InvoiceData.class);

    return result;
}
```

---

## 4. Validation & Business Rules

After the AI returns structured data, it must be validated before insertion into the database.

### 4.1 Validation Rules

| Rule | Check | Severity |
|---|---|---|
| **Required Fields** | `vendor_name`, `invoice_number`, `total_amount` must not be null | ERROR |
| **Date Validity** | `invoice_date` and `due_date` must be valid dates | WARNING |
| **Amount Non-Negative** | All monetary fields must be ≥ 0 | ERROR |
| **Line Item Consistency** | Each line item's `total_price` ≈ `quantity × unit_price` (±1% tolerance) | WARNING |
| **Invoice Total Consistency** | `subtotal + tax_amount - discount_amount ≈ total_amount` (±1% tolerance) | WARNING |
| **Line Items Sum** | `SUM(line_items.total_price) ≈ subtotal` (±5% tolerance) | WARNING |
| **Currency Valid** | `currency` is one of: INR, USD, EUR, GBP | ERROR |

### 4.2 Validation Implementation

```java
public class InvoiceValidator {

    public ValidationResult validate(InvoiceData invoice) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        // Required fields
        if (invoice.vendorName() == null || invoice.vendorName().isBlank()) {
            errors.add(new ValidationError("vendor_name", "Vendor name is required"));
        }
        if (invoice.totalAmount() == null) {
            errors.add(new ValidationError("total_amount", "Total amount is required"));
        }

        // Amount non-negative
        if (invoice.totalAmount() != null && invoice.totalAmount().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ValidationError("total_amount", "Total amount must be non-negative"));
        }

        // Invoice total consistency
        if (invoice.subtotal() != null && invoice.taxAmount() != null) {
            BigDecimal expected = invoice.subtotal()
                .add(invoice.taxAmount())
                .subtract(invoice.discountAmount() != null ? invoice.discountAmount() : BigDecimal.ZERO);

            if (!isWithinTolerance(expected, invoice.totalAmount(), 0.01)) {
                warnings.add(new ValidationWarning("total_amount",
                    "Total does not match subtotal + tax - discount. " +
                    "Expected: " + expected + ", Got: " + invoice.totalAmount()));
            }
        }

        return new ValidationResult(errors, warnings);
    }

    private boolean isWithinTolerance(BigDecimal expected, BigDecimal actual, double tolerancePercent) {
        if (expected == null || actual == null) return true;
        BigDecimal diff = expected.subtract(actual).abs();
        BigDecimal tolerance = expected.abs().multiply(BigDecimal.valueOf(tolerancePercent));
        return diff.compareTo(tolerance) <= 0;
    }
}
```

---

## 5. Confidence Score Calculation

The confidence score (0.0 to 1.0) represents how much we trust the AI's extraction. It determines whether a document goes to `COMPLETED` or `NEEDS_REVIEW`.

### 5.1 Scoring Formula

```
Base Score: 1.0

Deductions:
  - Missing required field:        -0.20 per field
  - Amount consistency mismatch:   -0.15
  - Line items sum mismatch:       -0.10
  - Missing optional fields:       -0.02 per field
  - Date parsing failure:          -0.05

Minimum Score: 0.0
```

### 5.2 Status Decision

| Confidence Score | Document Status | Meaning |
|---|---|---|
| ≥ 0.85 | `COMPLETED` | High confidence; auto-approved for review |
| 0.50 – 0.84 | `NEEDS_REVIEW` | Medium confidence; requires human audit |
| < 0.50 | `FAILED` | Low confidence; likely bad OCR or unsupported format |

---

## 6. Prompt Engineering Best Practices

### 6.1 System Prompt

```
You are a precise document parsing engine specialized in extracting
structured data from invoices, receipts, and financial documents.

Rules:
1. Return ONLY valid JSON. No explanations, no markdown, no commentary.
2. If a field is not found, set its value to null.
3. Dates must be in YYYY-MM-DD format.
4. All monetary amounts must be plain numbers (no currency symbols, no commas).
5. If the document contains multiple currencies, use the primary currency.
6. For Indian invoices, extract GSTIN numbers where available.
```

### 6.2 Common Pitfalls & Mitigations

| Pitfall | Mitigation |
|---|---|
| AI returns markdown-wrapped JSON (` ```json ... ``` `) | Strip markdown code fences before parsing |
| AI hallucinates fields not in the document | Post-validation catches impossible values |
| OCR errors in numbers (e.g., `1` vs `l` vs `I`) | Business rule validation catches arithmetic mismatches |
| Multiple invoices in one document | System prompt explicitly says "Extract the FIRST invoice only" |
| Non-English invoices | Specify language in prompt; Tesseract supports multiple languages |
