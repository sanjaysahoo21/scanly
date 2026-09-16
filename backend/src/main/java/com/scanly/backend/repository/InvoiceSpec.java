package com.scanly.backend.repository;

import com.scanly.backend.entity.Invoice;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * InvoiceSpec
 *
 * Builds a JPA Specification from optional search/filter parameters.
 * Only non-null / non-blank parameters generate WHERE clauses.
 *
 * Fields supported:
 *   - orgId        (required — always scoped to org)
 *   - q            (ILIKE text search across vendor_name, buyer_name, invoice_number)
 *   - isAudited    (true / false)
 *   - dateFrom     (invoice_date >= dateFrom)
 *   - dateTo       (invoice_date <= dateTo)
 *   - amountMin    (total_amount >= amountMin)
 *   - amountMax    (total_amount <= amountMax)
 */
public class InvoiceSpec {

    private InvoiceSpec() {}

    public static Specification<Invoice> build(
            UUID orgId,
            String q,
            Boolean isAudited,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal amountMin,
            BigDecimal amountMax
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always scope to org via the document → organization join
            predicates.add(cb.equal(
                root.join("document").get("organization").get("id"), orgId));

            // Full-text-style search across vendor name, buyer name, invoice number
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("vendorName")), pattern),
                    cb.like(cb.lower(root.get("buyerName")), pattern),
                    cb.like(cb.lower(root.get("invoiceNumber")), pattern)
                ));
            }

            // Audit status filter
            if (isAudited != null) {
                predicates.add(cb.equal(root.get("isAudited"), isAudited));
            }

            // Date range filter
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), dateTo));
            }

            // Amount range filter
            if (amountMin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), amountMin));
            }
            if (amountMax != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), amountMax));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
