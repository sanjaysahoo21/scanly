package com.scanly.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.LineItem;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * InvoiceValidationService
 *
 * Runs four math/tax checks against an invoice and persists the results
 * directly onto the Invoice entity (hasValidationErrors + validationIssues).
 *
 * Checks:
 *   1. Line item row check  — quantity × unitPrice ≈ totalPrice  (per row)
 *   2. Subtotal check       — sum of line item totalPrice ≈ invoice subtotal
 *   3. Total check          — subtotal + taxAmount − discountAmount ≈ totalAmount
 *   4. Tax rate plausibility — taxAmount / subtotal is between 0% and 50%
 *
 * All comparisons use a tolerance of ±1 (rounding differences in source docs).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceValidationService {

    private static final BigDecimal TOLERANCE = new BigDecimal("1.00");
    private static final BigDecimal TAX_UPPER  = new BigDecimal("0.50"); // 50% max sane tax rate

    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    /**
     * Validate an invoice by ID.  Returns the updated Invoice (with validation
     * fields populated).  Returns empty if the invoice is not found.
     */
    @Transactional
    public Optional<Invoice> validate(UUID invoiceId, UUID orgId) {
        return invoiceRepository.findByIdAndDocumentOrganizationId(invoiceId, orgId)
            .map(inv -> {
                runAndSave(inv);
                return inv;
            });
    }

    /**
     * Validate an already-loaded Invoice within the caller's transaction.
     * Saves the entity immediately after updating the validation fields.
     */
    @Transactional
    public Invoice validateAndSave(Invoice invoice) {
        runAndSave(invoice);
        return invoice;
    }

    // ── Core validation logic ──────────────────────────────────────────────────

    private void runAndSave(Invoice invoice) {
        List<String> issues = new ArrayList<>();

        List<LineItem> items = invoice.getLineItems();

        // ── Check 1: Row-level price check ────────────────────────────────────
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                LineItem item = items.get(i);
                BigDecimal qty   = safe(item.getQuantity(),   BigDecimal.ONE);
                BigDecimal price = safe(item.getUnitPrice(),  BigDecimal.ZERO);
                BigDecimal total = safe(item.getTotalPrice(), BigDecimal.ZERO);

                BigDecimal expected = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
                if (notWithinTolerance(expected, total)) {
                    issues.add(String.format(
                        "Line item %d (%s): %s × %s = %s but recorded as %s",
                        i + 1, trim(item.getDescription(), 30),
                        qty.stripTrailingZeros().toPlainString(),
                        price.toPlainString(),
                        expected.toPlainString(),
                        total.toPlainString()
                    ));
                }
            }
        }

        // ── Check 2: Line items sum vs subtotal ────────────────────────────────
        if (items != null && !items.isEmpty() && invoice.getSubtotal() != null) {
            BigDecimal lineSum = items.stream()
                .map(li -> safe(li.getTotalPrice(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

            BigDecimal subtotal = invoice.getSubtotal().setScale(2, RoundingMode.HALF_UP);
            if (notWithinTolerance(lineSum, subtotal)) {
                issues.add(String.format(
                    "Line items sum to %s but subtotal is %s (difference: %s)",
                    lineSum.toPlainString(),
                    subtotal.toPlainString(),
                    lineSum.subtract(subtotal).abs().toPlainString()
                ));
            }
        }

        // ── Check 3: subtotal + tax − discount = total ─────────────────────────
        if (invoice.getSubtotal() != null && invoice.getTotalAmount() != null) {
            BigDecimal sub      = safe(invoice.getSubtotal(),       BigDecimal.ZERO);
            BigDecimal tax      = safe(invoice.getTaxAmount(),      BigDecimal.ZERO);
            BigDecimal discount = safe(invoice.getDiscountAmount(), BigDecimal.ZERO);
            BigDecimal total    = invoice.getTotalAmount();

            BigDecimal computed = sub.add(tax).subtract(discount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal recorded = total.setScale(2, RoundingMode.HALF_UP);

            if (notWithinTolerance(computed, recorded)) {
                issues.add(String.format(
                    "Total mismatch: %s (subtotal) + %s (tax) − %s (discount) = %s but total is %s",
                    sub.toPlainString(), tax.toPlainString(), discount.toPlainString(),
                    computed.toPlainString(), recorded.toPlainString()
                ));
            }
        }

        // ── Check 4: Tax rate plausibility ────────────────────────────────────
        BigDecimal subtotal = invoice.getSubtotal();
        BigDecimal taxAmount = invoice.getTaxAmount();
        if (subtotal != null && taxAmount != null
                && subtotal.compareTo(BigDecimal.ZERO) > 0
                && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal impliedRate = taxAmount.divide(subtotal, 4, RoundingMode.HALF_UP);
            if (impliedRate.compareTo(TAX_UPPER) > 0) {
                issues.add(String.format(
                    "Unusually high tax: %.1f%% (tax %s on subtotal %s) — please verify",
                    impliedRate.multiply(BigDecimal.valueOf(100)).doubleValue(),
                    taxAmount.toPlainString(), subtotal.toPlainString()
                ));
            }
        }

        // ── Persist results ───────────────────────────────────────────────────
        invoice.setHasValidationErrors(!issues.isEmpty());
        try {
            invoice.setValidationIssues(objectMapper.writeValueAsString(issues));
        } catch (Exception e) {
            invoice.setValidationIssues("[]");
        }
        invoiceRepository.save(invoice);

        if (issues.isEmpty()) {
            log.info("Invoice {} passed all validation checks", invoice.getId());
        } else {
            log.warn("Invoice {} has {} validation issue(s): {}", invoice.getId(), issues.size(), issues);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean notWithinTolerance(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) > 0;
    }

    private BigDecimal safe(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private String trim(String s, int max) {
        if (s == null) return "?";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
