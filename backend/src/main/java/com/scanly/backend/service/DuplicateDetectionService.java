package com.scanly.backend.service;

import com.scanly.backend.entity.Invoice;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DuplicateDetectionService
 *
 * Checks a given invoice against existing invoices in the same org to detect
 * potential duplicates. Two strategies are used, applied in priority order:
 *
 *   1. STRONG match  — same invoice number (case-insensitive)
 *   2. FUZZY  match  — same vendor name + total amount + invoice date
 *
 * Results are stored directly on the Invoice entity:
 *   - isDuplicate    (true/false/null)
 *   - duplicateOfId  (UUID of the earlier matching invoice)
 *   - duplicateReason (human-readable explanation)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateDetectionService {

    private final InvoiceRepository invoiceRepository;

    /**
     * Detect duplicates for an invoice looked up by ID + org scope.
     * Returns the updated Invoice if found, empty if the invoice doesn't exist.
     */
    @Transactional
    public Optional<Invoice> detect(UUID invoiceId, UUID orgId) {
        return invoiceRepository.findByIdAndDocumentOrganizationId(invoiceId, orgId)
            .map(inv -> {
                runAndSave(inv, orgId);
                return inv;
            });
    }

    /**
     * Detect duplicates for an already-loaded invoice within the caller's transaction.
     * The org ID is extracted from the invoice's document → organization.
     */
    @Transactional
    public Invoice detectAndSave(Invoice invoice) {
        UUID orgId = invoice.getDocument().getOrganization().getId();
        runAndSave(invoice, orgId);
        return invoice;
    }

    // ── Core logic ─────────────────────────────────────────────────────────────

    private void runAndSave(Invoice invoice, UUID orgId) {
        UUID excludeId = invoice.getId();

        // ── Strategy 1: Same invoice number ──────────────────────────────────
        if (invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()) {
            List<Invoice> matches = invoiceRepository.findSameInvoiceNumber(
                orgId, invoice.getInvoiceNumber(), excludeId);

            if (!matches.isEmpty()) {
                Invoice match = matches.get(0);
                markDuplicate(invoice, match, "Same invoice number as " + label(match));
                log.warn("Invoice {} flagged as duplicate (same invoice number) of {}",
                    invoice.getId(), match.getId());
                return;
            }
        }

        // ── Strategy 2: Same vendor + amount + date ───────────────────────────
        if (invoice.getVendorName() != null
                && invoice.getTotalAmount() != null
                && invoice.getInvoiceDate() != null) {

            List<Invoice> matches = invoiceRepository.findSameVendorAmountDate(
                orgId,
                invoice.getVendorName(),
                invoice.getTotalAmount(),
                invoice.getInvoiceDate(),
                excludeId);

            if (!matches.isEmpty()) {
                Invoice match = matches.get(0);
                markDuplicate(invoice, match,
                    String.format("Same vendor (%s), amount (%s), and date (%s) as %s",
                        invoice.getVendorName(),
                        invoice.getTotalAmount().toPlainString(),
                        invoice.getInvoiceDate(),
                        label(match)));
                log.warn("Invoice {} flagged as probable duplicate (vendor+amount+date) of {}",
                    invoice.getId(), match.getId());
                return;
            }
        }

        // ── No duplicate found ────────────────────────────────────────────────
        invoice.setIsDuplicate(false);
        invoice.setDuplicateOfId(null);
        invoice.setDuplicateReason(null);
        invoiceRepository.save(invoice);
        log.info("Invoice {} passed duplicate check", invoice.getId());
    }

    private void markDuplicate(Invoice invoice, Invoice match, String reason) {
        invoice.setIsDuplicate(true);
        invoice.setDuplicateOfId(match.getId());
        invoice.setDuplicateReason(reason);
        invoiceRepository.save(invoice);
    }

    /** Returns a short human-readable label for an invoice (number or short ID). */
    private String label(Invoice inv) {
        if (inv.getInvoiceNumber() != null && !inv.getInvoiceNumber().isBlank()) {
            return "invoice \"" + inv.getInvoiceNumber() + "\"";
        }
        return "invoice " + inv.getId().toString().substring(0, 8) + "…";
    }
}
