package com.scanly.backend.controller;

import com.scanly.backend.dto.InvoiceResponse;
import com.scanly.backend.entity.User;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private static final int MAX_PAGE_SIZE = 100;
    private final InvoiceRepository invoiceRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(@AuthenticationPrincipal User currentUser,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "INVALID_PAGINATION", "message", "page must be non-negative and size must be 1-100"));
        }
        var result = invoiceRepository.findByDocumentOrganizationId(currentUser.getOrganization().getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(java.util.Map.of(
            "invoices", result.getContent().stream().map(InvoiceResponse::from).toList(),
            "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages(), "currentPage", result.getNumber()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return invoiceRepository.findByIdAndDocumentOrganizationId(id, currentUser.getOrganization().getId())
            .<ResponseEntity<?>>map(invoice -> ResponseEntity.ok(InvoiceResponse.from(invoice)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
