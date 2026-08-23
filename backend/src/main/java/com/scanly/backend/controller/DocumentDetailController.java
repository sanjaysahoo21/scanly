package com.scanly.backend.controller;

import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.User;
import com.scanly.backend.repository.AuditLogRepository;
import com.scanly.backend.repository.InvoiceRepository;
import com.scanly.backend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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

    /**
     * Full document detail: document metadata + extracted invoice (if any) + recent audit logs.
     */
    @GetMapping("/{id}/detail")
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
                Invoice saved = invoiceRepository.save(existing);
                return ResponseEntity.ok(saved);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
