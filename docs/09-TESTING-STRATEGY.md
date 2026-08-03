# 🧪 Testing Strategy

---

## 1. Testing Pyramid

```
                 ┌───────────┐
                 │   E2E     │  ← Few, slow, high-value
                 │  Tests    │     (Selenium / Playwright)
                 ├───────────┤
                 │Integration│  ← Moderate number
                 │  Tests    │     (Spring Boot Test + Testcontainers)
                 ├───────────┤
                 │   Unit    │  ← Many, fast, isolated
                 │  Tests    │     (JUnit 5 + Mockito)
                 └───────────┘
```

---

## 2. Unit Tests

Unit tests validate individual classes and methods in isolation, using mocks for dependencies.

### 2.1 What to Unit Test

| Component | Test Cases |
|---|---|
| **InvoiceValidator** | Required fields missing, negative amounts, total mismatch, valid invoice passes |
| **ConfidenceScorer** | Score calculation for various validation outcomes |
| **TextExtractionDecider** | Correct routing: PDF → Tika, Image → Tesseract, empty Tika → fallback OCR |
| **DTOs / Mappers** | Entity-to-DTO and DTO-to-entity conversion |
| **JWT Utility** | Token generation, parsing, expiry validation |

### 2.2 Example: InvoiceValidator Unit Test

```java
@ExtendWith(MockitoExtension.class)
class InvoiceValidatorTest {

    private InvoiceValidator validator = new InvoiceValidator();

    @Test
    void shouldPassValidation_whenAllFieldsPresent() {
        InvoiceData invoice = new InvoiceData(
            "INV-001", "ACME Corp", null, null, null, null, null,
            LocalDate.of(2026, 3, 15), null,
            new BigDecimal("10000"), new BigDecimal("1800"),
            BigDecimal.ZERO, new BigDecimal("11800"),
            "INR", List.of(/* line items */)
        );

        ValidationResult result = validator.validate(invoice);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void shouldReturnError_whenVendorNameMissing() {
        InvoiceData invoice = new InvoiceData(
            "INV-001", null, /* vendor_name is null */ ...
        );

        ValidationResult result = validator.validate(invoice);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
            .extracting("fieldName")
            .contains("vendor_name");
    }

    @Test
    void shouldReturnWarning_whenTotalMismatch() {
        // subtotal(10000) + tax(1800) - discount(0) = 11800
        // but total_amount is set to 12000 (mismatch!)
        InvoiceData invoice = new InvoiceData(
            "INV-001", "ACME Corp", null, null, null, null, null,
            LocalDate.now(), null,
            new BigDecimal("10000"), new BigDecimal("1800"),
            BigDecimal.ZERO, new BigDecimal("12000"),
            "INR", List.of()
        );

        ValidationResult result = validator.validate(invoice);

        assertThat(result.getWarnings())
            .extracting("fieldName")
            .contains("total_amount");
    }

    @Test
    void shouldReturnError_whenAmountIsNegative() {
        InvoiceData invoice = new InvoiceData(
            "INV-001", "ACME Corp", null, null, null, null, null,
            LocalDate.now(), null,
            new BigDecimal("10000"), new BigDecimal("1800"),
            BigDecimal.ZERO, new BigDecimal("-500"),
            "INR", List.of()
        );

        ValidationResult result = validator.validate(invoice);

        assertThat(result.hasErrors()).isTrue();
    }
}
```

### 2.3 Running Unit Tests

```bash
cd backend
./mvnw test
```

---

## 3. Integration Tests

Integration tests verify that components work together correctly, using real databases and message brokers (via Testcontainers).

### 3.1 What to Integration Test

| Test Scope | Description |
|---|---|
| **Document Upload Flow** | Upload a file → verify DB record created → verify message published to queue |
| **Worker Processing** | Consume a queue message → extract text → parse with AI → verify DB records |
| **Invoice CRUD** | Create, read, update invoices → verify audit logs generated |
| **Authentication** | Register → login → access protected endpoint → verify JWT works |
| **Status Polling** | Upload → verify PENDING → process → verify COMPLETED |

### 3.2 Testcontainers Setup

```java
@SpringBootTest
@Testcontainers
class DocumentUploadIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("docparser_test");

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void shouldAcceptUpload_andCreatePendingDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "files", "test-invoice.pdf",
            "application/pdf",
            getClass().getResourceAsStream("/test-data/sample-invoice.pdf")
        );

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + getTestToken()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobs[0].status").value("PENDING"));

        // Verify document was persisted
        List<Document> docs = documentRepository.findAll();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getStatus()).isEqualTo(DocumentStatus.PENDING);
    }
}
```

### 3.3 Running Integration Tests

```bash
cd backend
./mvnw verify -Pintegration-tests
```

---

## 4. End-to-End (E2E) Tests

E2E tests validate the entire user flow through the real UI using a browser automation tool.

### 4.1 Tool: Playwright

```typescript
// tests/e2e/upload-and-audit.spec.ts
import { test, expect } from '@playwright/test';

test('full upload and audit flow', async ({ page }) => {
  // 1. Login
  await page.goto('/login');
  await page.fill('#email', 'test@example.com');
  await page.fill('#password', 'password123');
  await page.click('#login-button');
  await expect(page).toHaveURL('/');

  // 2. Upload a document
  await page.goto('/documents/upload');
  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles('tests/fixtures/sample-invoice.pdf');
  await page.click('#upload-button');
  await expect(page.locator('.upload-success')).toBeVisible();

  // 3. Wait for processing
  await page.goto('/documents');
  await expect(page.locator('.status-badge')).toHaveText('COMPLETED', {
    timeout: 30000 // wait up to 30s for processing
  });

  // 4. Open audit view
  await page.click('.document-row:first-child');
  await expect(page.locator('#pdf-viewer')).toBeVisible();
  await expect(page.locator('#invoice-form')).toBeVisible();

  // 5. Edit a field
  await page.fill('#vendor-name', 'Updated Vendor Name');
  await page.click('#save-button');
  await expect(page.locator('.save-success')).toBeVisible();

  // 6. Verify audit log
  await page.goto('/audit-logs');
  await expect(page.locator('.audit-row')).toContainText('vendor_name');
  await expect(page.locator('.audit-row')).toContainText('Updated Vendor Name');
});
```

### 4.2 Running E2E Tests

```bash
cd frontend
npx playwright install
npx playwright test
```

---

## 5. Test Data

### 5.1 Sample Test Files

Store test fixtures in `backend/src/test/resources/test-data/`:

| File | Description |
|---|---|
| `sample-invoice.pdf` | Clean, digitally generated invoice PDF |
| `scanned-invoice.jpg` | Photographed/scanned invoice (tests OCR) |
| `multi-page-invoice.pdf` | Invoice spanning multiple pages |
| `blurry-scan.jpg` | Low-quality scan (tests error handling) |
| `non-invoice.pdf` | A document that is not an invoice (tests rejection) |
| `empty.pdf` | Empty PDF file (tests edge case) |
| `large-invoice.pdf` | Invoice with 50+ line items (tests performance) |

### 5.2 Expected Outputs

For each test file, maintain a matching expected JSON output in `test-data/expected/`:

```
test-data/
├── sample-invoice.pdf
├── scanned-invoice.jpg
└── expected/
    ├── sample-invoice.json      # Expected parsed output
    └── scanned-invoice.json     # Expected parsed output
```

---

## 6. Test Coverage Goals

| Layer | Target Coverage | Priority Fields |
|---|---|---|
| **Validation Engine** | ≥ 90% | All validation rules, edge cases |
| **Service Layer** | ≥ 80% | Business logic, error handling |
| **Controllers** | ≥ 70% | Request validation, status codes, auth |
| **Repositories** | Covered by integration tests | Custom queries |
| **AI Parsing** | Mocked in unit tests, real in integration | Schema compliance |

### Generate Coverage Report

```bash
cd backend
./mvnw test jacoco:report
# Report: target/site/jacoco/index.html
```
