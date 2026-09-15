package com.scanly.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanly.backend.dto.InvoiceResponse;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.User;
import com.scanly.backend.repository.InvoiceRepository;
import com.scanly.backend.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private static final int MAX_PAGE_SIZE = 100;
    private final InvoiceRepository invoiceRepository;
    private final ExportService exportService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(@AuthenticationPrincipal User currentUser,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PAGINATION",
                "message", "page must be non-negative and size must be 1-100"));
        }
        var result = invoiceRepository.findByDocumentOrganizationId(
            currentUser.getOrganization().getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
            "invoices", result.getContent().stream().map(InvoiceResponse::from).toList(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "currentPage", result.getNumber()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return invoiceRepository.findByIdAndDocumentOrganizationId(id, currentUser.getOrganization().getId())
            .<ResponseEntity<?>>map(invoice -> ResponseEntity.ok(InvoiceResponse.from(invoice)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Export all invoices for the current org.
     * GET /api/v1/invoices/export?format=csv  (default)
     * GET /api/v1/invoices/export?format=json
     */
    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportAll(@AuthenticationPrincipal User currentUser,
                                       @RequestParam(defaultValue = "csv") String format) {
        if ("json".equalsIgnoreCase(format)) {
            List<Invoice> invoices = invoiceRepository.findByDocumentOrganizationId(
                currentUser.getOrganization().getId(), Pageable.unpaged()).getContent();
            try {
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(invoices.stream().map(InvoiceResponse::from).toList());
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }
        // Default: CSV
        byte[] csv = exportService.exportInvoicesCsv(currentUser.getOrganization());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    /**
     * Export a single invoice with full line items.
     * GET /api/v1/invoices/{id}/export?format=csv|json
     */
    @GetMapping("/{id}/export")
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportSingle(@PathVariable UUID id,
                                          @AuthenticationPrincipal User currentUser,
                                          @RequestParam(defaultValue = "csv") String format) {
        return invoiceRepository.findByIdAndDocumentOrganizationId(id, currentUser.getOrganization().getId())
            .<ResponseEntity<?>>map(invoice -> {
                if ("json".equalsIgnoreCase(format)) {
                    try {
                        byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsBytes(InvoiceResponse.from(invoice));
                        String filename = "invoice-" + (invoice.getInvoiceNumber() != null
                            ? invoice.getInvoiceNumber().replaceAll("[^a-zA-Z0-9_-]", "_") : invoice.getId()) + ".json";
                        return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(json);
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
                    }
                }
                byte[] csv = exportService.exportSingleInvoiceCsv(invoice);
                String filename = "invoice-" + (invoice.getInvoiceNumber() != null
                    ? invoice.getInvoiceNumber().replaceAll("[^a-zA-Z0-9_-]", "_") : invoice.getId()) + ".csv";
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
