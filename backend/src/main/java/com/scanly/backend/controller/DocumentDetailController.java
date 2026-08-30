package com.scanly.backend.controller;

import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.LineItem;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.repository.AuditLogRepository;
import com.scanly.backend.repository.InvoiceRepository;
import com.scanly.backend.service.DocumentService;
import com.scanly.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Document Detail Controller.
 *
 * GET /api/v1/documents/{id}/detail  — full detail: document + invoice + audit history
 * PUT /api/v1/documents/{id}/invoice — update invoice fields
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentDetailController {

    private final DocumentService documentService;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    /**
     * Stream the raw PDF file to the browser for inline preview.
     * GET /api/v1/documents/{id}/file
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<?> getDocumentFile(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        return documentService.getDocumentById(id, currentUser)
            .map(doc -> {
                try {
                    Path filePath = Path.of(doc.getFilePath());
                    if (!Files.exists(filePath)) {
                        return ResponseEntity.notFound().build();
                    }
                    byte[] bytes = Files.readAllBytes(filePath);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    // Must use raw header — setContentDispositionFormData sets form-data not inline
                    headers.set(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + doc.getFileName().replace("\"", "") + "\"");
                    headers.setContentLength(bytes.length);
                    return ResponseEntity.ok()
                        .headers(headers)
                        .body(new ByteArrayResource(bytes));
                } catch (IOException e) {
                    return ResponseEntity.internalServerError()
                        .body("Could not read file: " + e.getMessage());
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Full document detail: document metadata + extracted invoice (if any) + recent audit logs.
     */
    @GetMapping("/{id}/detail")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocumentDetail(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        return documentService.getDocumentById(id, currentUser)
            .map(doc -> {
                Invoice invoice = invoiceRepository.findByDocumentId(id).orElse(null);
                var auditLogs = auditLogRepository.findByDocumentId(id, PageRequest.of(0, 50));

                return ResponseEntity.ok(Map.of(
                    "document", doc,
                    "invoice", invoice != null ? invoice : Map.of(),
                    "auditLogs", auditLogs.getContent()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update invoice fields (called when auditor edits extracted data).
     * TODO: Write an AuditLog entry for each changed field.
     */
    @PutMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<?> updateInvoice(
        @PathVariable UUID id,
        @RequestBody Invoice updatedInvoice,
        @AuthenticationPrincipal User currentUser
    ) {
        return documentService.getDocumentById(id, currentUser)
            .map(doc -> {
                Invoice existing = invoiceRepository.findByDocumentId(id).orElse(null);
                if (existing == null) {
                    return ResponseEntity.notFound().<Invoice>build();
                }
                String previousTotal = String.valueOf(existing.getTotalAmount());
                // Apply editable fields
                existing.setInvoiceNumber(updatedInvoice.getInvoiceNumber());
                existing.setVendorName(updatedInvoice.getVendorName());
                existing.setVendorAddress(updatedInvoice.getVendorAddress());
                existing.setVendorGstin(updatedInvoice.getVendorGstin());
                existing.setBuyerName(updatedInvoice.getBuyerName());
                existing.setBuyerAddress(updatedInvoice.getBuyerAddress());
                existing.setBuyerGstin(updatedInvoice.getBuyerGstin());
                existing.setInvoiceDate(updatedInvoice.getInvoiceDate());
                existing.setDueDate(updatedInvoice.getDueDate());
                existing.setSubtotal(updatedInvoice.getSubtotal());
                existing.setTaxAmount(updatedInvoice.getTaxAmount());
                existing.setDiscountAmount(updatedInvoice.getDiscountAmount());
                existing.setTotalAmount(updatedInvoice.getTotalAmount());
                existing.setCurrency(updatedInvoice.getCurrency());
                syncLineItems(existing, updatedInvoice.getLineItems());
                Invoice saved = invoiceRepository.save(existing);
                auditService.recordInvoice(saved, currentUser, AuditAction.EDIT, "invoice", previousTotal,
                    String.valueOf(saved.getTotalAmount()));
                return ResponseEntity.ok(saved);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Reprocess a document on-demand.
     */
    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<?> reprocessDocument(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        boolean triggered = documentService.reprocessDocument(id, currentUser);
        if (triggered) {
            documentService.getDocumentById(id, currentUser)
                .ifPresent(document -> auditService.recordDocument(document, currentUser, AuditAction.REPROCESS,
                    "status", document.getStatus().name(), "PROCESSING"));
            return ResponseEntity.ok(Map.of("message", "Reprocessing started", "documentId", id));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<?> approveInvoice(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return documentService.getDocumentById(id, currentUser).map(document -> {
            Invoice invoice = invoiceRepository.findByDocumentId(id).orElse(null);
            if (invoice == null) return ResponseEntity.notFound().build();
            if (!Boolean.TRUE.equals(invoice.getIsAudited())) {
                invoice.setIsAudited(true);
                invoice.setAuditedBy(currentUser);
                invoice.setAuditedAt(java.time.Instant.now());
                invoice = invoiceRepository.save(invoice);
                auditService.recordInvoice(invoice, currentUser, AuditAction.APPROVE, "isAudited", "false", "true");
            }
            return ResponseEntity.ok(invoice);
        }).orElse(ResponseEntity.notFound().build());
    }

    private void syncLineItems(Invoice invoice, List<LineItem> submitted) {
        invoice.getLineItems().clear();
        if (submitted == null) return;
        for (LineItem item : submitted) {
            invoice.getLineItems().add(LineItem.builder()
                .invoice(invoice)
                .description(item.getDescription() == null || item.getDescription().isBlank() ? "Item" : item.getDescription().trim())
                .hsnCode(item.getHsnCode())
                .quantity(item.getQuantity() == null ? java.math.BigDecimal.ONE : item.getQuantity())
                .unitPrice(item.getUnitPrice() == null ? java.math.BigDecimal.ZERO : item.getUnitPrice())
                .taxRate(item.getTaxRate() == null ? java.math.BigDecimal.ZERO : item.getTaxRate())
                .totalPrice(item.getTotalPrice() == null ? java.math.BigDecimal.ZERO : item.getTotalPrice())
                .build());
        }
    }
}
