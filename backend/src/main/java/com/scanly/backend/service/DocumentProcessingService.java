package com.scanly.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.LineItem;
import com.scanly.backend.entity.enums.Currency;
import com.scanly.backend.entity.enums.DocumentStatus;
import com.scanly.backend.repository.DocumentRepository;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DocumentProcessingService
 *
 * Handles the full AI processing pipeline for a single document:
 *   1. Update document status → PROCESSING
 *   2. Call GeminiOcrService to extract invoice JSON
 *   3. Parse the JSON and save Invoice + LineItems to database
 *   4. Update document status → COMPLETED (or FAILED on error)
 *
 * Methods are @Async so they run in a background thread and
 * don't block the upload HTTP response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final InvoiceRepository invoiceRepository;
    private final GroqAiService groqAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${scanly.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Process a single document asynchronously.
     * Takes documentId to ensure clean transaction isolation across async threads.
     */
    @Async
    public void processDocument(UUID documentId) {
        try {
            doProcessDocument(documentId);
        } catch (Exception e) {
            log.error("Unhandled error processing document {}: {}", documentId, e.getMessage(), e);
            markFailed(documentId, e.getMessage());
        }
    }

    /**
     * Inner method that does the actual processing in its own transaction.
     * If this throws, the transaction rolls back — but the @Async wrapper
     * above will catch it and call markFailed() in a separate new transaction.
     */
    @Transactional
    public void doProcessDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Cannot process document: ID {} not found", documentId);
            return;
        }

        log.info("Starting AI processing for document: {} ({})", document.getFileName(), document.getId());

        // Step 1: Mark as PROCESSING
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.saveAndFlush(document);

        // Step 2: Read the file from disk & call Groq AI extraction
        Path filePath = Path.of(document.getFilePath());
        String rawJson;
        try {
            rawJson = groqAiService.extractAndStructureInvoice(filePath, document.getFileName());
        } catch (Exception e) {
            log.error("AI extraction failed for document {}: {}", documentId, e.getMessage(), e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
            return;
        }

        // Step 3: Parse and save invoice
        try {
            Invoice invoice = parseAndSaveInvoice(document, rawJson);

            // Step 4: Update document with confidence score and status
            document.setStatus(DocumentStatus.COMPLETED);
            document.setRawExtractedText(rawJson);

            double confidence = calculateConfidence(invoice);
            document.setConfidenceScore(BigDecimal.valueOf(confidence));

            if (confidence < 0.6) {
                document.setStatus(DocumentStatus.NEEDS_REVIEW);
                log.warn("Document {} flagged for review (confidence: {})", document.getId(), confidence);
            }

            documentRepository.save(document);
            log.info("Successfully processed document: {} → status: {}", document.getId(), document.getStatus());
        } catch (Exception e) {
            log.error("Failed to parse/save invoice for document {}: {}", documentId, e.getMessage(), e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("Invoice parsing failed: " + e.getMessage());
            documentRepository.save(document);
        }
    }

    /**
     * Mark a document as FAILED in a brand-new transaction so the status
     * is always persisted even if the main transaction rolled back.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markFailed(UUID documentId, String reason) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(reason);
            documentRepository.save(doc);
        });
    }

    /**
     * Parse the Gemini JSON response and save Invoice + LineItems.
     */
    private Invoice parseAndSaveInvoice(Document document, String rawJson) throws Exception {
        // Strip any accidental markdown code fences Gemini sometimes adds
        String cleanJson = rawJson
            .replaceAll("(?s)^```json\\s*", "")
            .replaceAll("(?s)```\\s*$", "")
            .trim();

        JsonNode root = objectMapper.readTree(cleanJson);

        // Check if an invoice already exists for this document to avoid unique constraint violations
        Invoice invoice = invoiceRepository.findByDocumentId(document.getId()).orElse(new Invoice());

        // Update Invoice entity from extracted fields
        invoice.setDocument(document);
        invoice.setInvoiceNumber(textOrNull(root, "invoiceNumber"));
        invoice.setVendorName(textOrNull(root, "vendorName"));
        invoice.setVendorAddress(textOrNull(root, "vendorAddress"));
        invoice.setVendorGstin(textOrNull(root, "vendorGstin"));
        invoice.setBuyerName(textOrNull(root, "buyerName"));
        invoice.setBuyerAddress(textOrNull(root, "buyerAddress"));
        invoice.setBuyerGstin(textOrNull(root, "buyerGstin"));
        invoice.setInvoiceDate(dateOrNull(root, "invoiceDate"));
        invoice.setDueDate(dateOrNull(root, "dueDate"));
        invoice.setSubtotal(decimalOrNull(root, "subtotal"));
        invoice.setTaxAmount(decimalOrNull(root, "taxAmount"));
        invoice.setDiscountAmount(decimalOrDefault(root, "discountAmount"));
        invoice.setTotalAmount(decimalOrNull(root, "totalAmount"));
        invoice.setCurrency(currencyOrDefault(root, "currency"));
        if (invoice.getId() == null) {
            invoice.setIsAudited(false);
        }
        // Save invoice first (needed for LineItem foreign key)
        invoice = invoiceRepository.save(invoice);

        // Parse line items
        JsonNode lineItemsNode = root.path("lineItems");
        if (lineItemsNode.isArray()) {
            List<LineItem> lineItems = new ArrayList<>();
            for (JsonNode item : lineItemsNode) {
                LineItem lineItem = LineItem.builder()
                    .invoice(invoice)
                    .description(item.path("description").asText("Unknown item"))
                    .hsnCode(textOrNull(item, "hsnCode"))
                    .quantity(decimalOrDefault(item, "quantity", BigDecimal.ONE))
                    .unitPrice(decimalOrDefault(item, "unitPrice", BigDecimal.ZERO))
                    .taxRate(decimalOrNull(item, "taxRate"))
                    .totalPrice(decimalOrDefault(item, "totalPrice", BigDecimal.ZERO))
                    .build();
                lineItems.add(lineItem);
            }
            invoice.getLineItems().clear();
            invoice.getLineItems().addAll(lineItems);
            invoice = invoiceRepository.save(invoice);
        }

        return invoice;
    }

    /**
     * Estimate confidence 0.0–1.0 based on how many key fields were extracted.
     */
    private double calculateConfidence(Invoice invoice) {
        int totalFields = 8;
        int filledFields = 0;
        if (invoice.getInvoiceNumber() != null) filledFields++;
        if (invoice.getVendorName()    != null) filledFields++;
        if (invoice.getBuyerName()     != null) filledFields++;
        if (invoice.getInvoiceDate()   != null) filledFields++;
        if (invoice.getTotalAmount()   != null) filledFields++;
        if (invoice.getSubtotal()      != null) filledFields++;
        if (invoice.getTaxAmount()     != null) filledFields++;
        if (!invoice.getLineItems().isEmpty())  filledFields++;
        return (double) filledFields / totalFields;
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private String textOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return (val.isNull() || val.isMissingNode() || val.asText().isBlank()) ? null : val.asText();
    }

    private LocalDate dateOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) return null;
        try { return LocalDate.parse(text); } catch (Exception e) { return null; }
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        if (val.isNull() || val.isMissingNode()) return null;
        try { return val.decimalValue(); } catch (Exception e) { return null; }
    }

    private BigDecimal decimalOrDefault(JsonNode node, String field) {
        BigDecimal val = decimalOrNull(node, field);
        return val != null ? val : BigDecimal.ZERO;
    }

    private BigDecimal decimalOrDefault(JsonNode node, String field, BigDecimal defaultValue) {
        BigDecimal val = decimalOrNull(node, field);
        return val != null ? val : defaultValue;
    }

    private Currency currencyOrDefault(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) return Currency.INR;
        try { return Currency.valueOf(text.toUpperCase()); } catch (Exception e) { return Currency.INR; }
    }
}
