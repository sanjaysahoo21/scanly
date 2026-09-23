package com.scanly.backend.controller;

import com.scanly.backend.dto.InvoiceResponse;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.User;
import com.scanly.backend.repository.InvoiceRepository;
import com.scanly.backend.repository.InvoiceSpec;
import com.scanly.backend.service.ExportService;
import com.scanly.backend.service.DuplicateDetectionService;
import com.scanly.backend.service.InvoiceValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private static final int MAX_PAGE_SIZE = 100;
    private final InvoiceRepository invoiceRepository;
    private final ExportService exportService;
    private final InvoiceValidationService validationService;
    private final DuplicateDetectionService duplicateDetectionService;

    /**
     * List / search invoices for the current org.
     *
     * GET /api/v1/invoices
     *   ?q=vendor_or_invoice_text   — text search (vendor name, buyer name, invoice number)
     *   ?audited=true|false         — filter by audit status (omit for all)
     *   ?dateFrom=YYYY-MM-DD        — invoice date ≥ dateFrom
     *   ?dateTo=YYYY-MM-DD          — invoice date ≤ dateTo
     *   ?amountMin=100              — total amount ≥ amountMin
     *   ?amountMax=5000             — total amount ≤ amountMax
     *   ?page=0&size=20             — pagination
     *   ?sort=invoiceDate,desc      — sorting (field,direction)
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean audited,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "invoiceDate,desc") String sort) {

        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PAGINATION",
                "message", "page must be non-negative and size must be 1-100"));
        }

        // Parse sort param: "field,direction"
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0].trim();
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim())
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        // Parse optional date strings
        LocalDate from = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate to = dateTo != null && !dateTo.isBlank() ? LocalDate.parse(dateTo) : null;

        var spec = InvoiceSpec.build(
            currentUser.getOrganization().getId(), q, audited, from, to, amountMin, amountMax);

        Page<Invoice> result = invoiceRepository.findAll(spec, pageable);

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
     * Manually (re-)run math and tax validation on an invoice.
     * Useful after the user edits invoice fields and wants fresh validation results.
     * POST /api/v1/invoices/{id}/validate
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<?> validate(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return validationService.validate(id, currentUser.getOrganization().getId())
            .<ResponseEntity<?>>map(inv -> ResponseEntity.ok(InvoiceResponse.from(inv)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * (Re-)run duplicate detection on an invoice.
     * POST /api/v1/invoices/{id}/detect-duplicates
     */
    @PostMapping("/{id}/detect-duplicates")
    public ResponseEntity<?> detectDuplicates(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return duplicateDetectionService.detect(id, currentUser.getOrganization().getId())
            .<ResponseEntity<?>>map(inv -> ResponseEntity.ok(InvoiceResponse.from(inv)))
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
        try {
            UUID orgId = currentUser.getOrganization().getId();
            if ("json".equalsIgnoreCase(format)) {
                byte[] json = exportService.exportAllInvoicesJson(orgId);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
            }
            byte[] csv = exportService.exportAllInvoicesCsv(orgId);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
        } catch (Exception e) {
            log.error("Export all invoices failed (format={})", format, e);
            return ResponseEntity.internalServerError().body(Map.of("error", String.valueOf(e.getMessage())));
        }
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
        try {
            UUID orgId = currentUser.getOrganization().getId();
            if ("json".equalsIgnoreCase(format)) {
                return exportService.exportSingleInvoiceJson(id, orgId)
                    .<ResponseEntity<?>>map(json -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".json\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json))
                    .orElseGet(() -> ResponseEntity.notFound().build());
            }
            return exportService.exportSingleInvoiceCsv(id, orgId)
                .<ResponseEntity<?>>map(csv -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv))
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Export single invoice failed (id={}, format={})", id, format, e);
            return ResponseEntity.internalServerError().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * Export a specific selection of invoices.
     * POST /api/v1/invoices/export/batch?format=csv|json
     * Body: { "ids": ["uuid1", "uuid2", ...] }
     */
    @PostMapping("/export/batch")
    @Transactional(readOnly = true)
    public ResponseEntity<?> exportBatch(@AuthenticationPrincipal User currentUser,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestBody Map<String, List<UUID>> body) {
        Collection<UUID> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        try {
            UUID orgId = currentUser.getOrganization().getId();
            if ("json".equalsIgnoreCase(format)) {
                byte[] json = exportService.exportSelectedInvoicesJson(ids, orgId);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices-selected.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
            }
            byte[] csv = exportService.exportSelectedInvoicesCsv(ids, orgId);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices-selected.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
        } catch (Exception e) {
            log.error("Export batch invoices failed (format={})", format, e);
            return ResponseEntity.internalServerError().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
