package com.scanly.backend.service;

import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.LineItem;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.repository.AuditLogRepository;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ExportService
 *
 * Generates CSV and JSON byte payloads for:
 *   - All invoices (with line item summaries) scoped to an organization
 *   - A single invoice with full line items breakdown
 *   - Audit logs scoped to an organization, with optional action filter
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;

    // ── Invoice Exports ────────────────────────────────────────────────────────

    /**
     * Generates a CSV byte array for all invoices belonging to an organization.
     */
    @Transactional(readOnly = true)
    public byte[] exportInvoicesCsv(Organization org) {
        List<Invoice> invoices = invoiceRepository.findByDocumentOrganizationId(
            org.getId(), Pageable.unpaged()).getContent();
        StringBuilder sb = new StringBuilder();
        sb.append("Invoice Number,Vendor Name,Vendor GSTIN,Buyer Name,Buyer GSTIN,")
          .append("Invoice Date,Due Date,Subtotal,Tax Amount,Discount Amount,Total Amount,Currency,Is Audited,Line Items Count\n");
        for (Invoice inv : invoices) {
            sb.append(csv(inv.getInvoiceNumber())).append(',')
              .append(csv(inv.getVendorName())).append(',')
              .append(csv(inv.getVendorGstin())).append(',')
              .append(csv(inv.getBuyerName())).append(',')
              .append(csv(inv.getBuyerGstin())).append(',')
              .append(csv(inv.getInvoiceDate())).append(',')
              .append(csv(inv.getDueDate())).append(',')
              .append(csv(inv.getSubtotal())).append(',')
              .append(csv(inv.getTaxAmount())).append(',')
              .append(csv(inv.getDiscountAmount())).append(',')
              .append(csv(inv.getTotalAmount())).append(',')
              .append(csv(inv.getCurrency())).append(',')
              .append(inv.getIsAudited() != null && inv.getIsAudited() ? "Yes" : "No").append(',')
              .append(inv.getLineItems() != null ? inv.getLineItems().size() : 0)
              .append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates a detailed CSV for a single invoice including all line items.
     */
    @Transactional(readOnly = true)
    public byte[] exportSingleInvoiceCsv(Invoice inv) {
        StringBuilder sb = new StringBuilder();

        // Invoice header section
        sb.append("=== INVOICE SUMMARY ===\n");
        sb.append("Invoice Number,").append(csv(inv.getInvoiceNumber())).append('\n');
        sb.append("Vendor Name,").append(csv(inv.getVendorName())).append('\n');
        sb.append("Vendor Address,").append(csv(inv.getVendorAddress())).append('\n');
        sb.append("Vendor GSTIN,").append(csv(inv.getVendorGstin())).append('\n');
        sb.append("Buyer Name,").append(csv(inv.getBuyerName())).append('\n');
        sb.append("Buyer Address,").append(csv(inv.getBuyerAddress())).append('\n');
        sb.append("Buyer GSTIN,").append(csv(inv.getBuyerGstin())).append('\n');
        sb.append("Invoice Date,").append(csv(inv.getInvoiceDate())).append('\n');
        sb.append("Due Date,").append(csv(inv.getDueDate())).append('\n');
        sb.append("Subtotal,").append(csv(inv.getSubtotal())).append('\n');
        sb.append("Tax Amount,").append(csv(inv.getTaxAmount())).append('\n');
        sb.append("Discount Amount,").append(csv(inv.getDiscountAmount())).append('\n');
        sb.append("Total Amount,").append(csv(inv.getTotalAmount())).append('\n');
        sb.append("Currency,").append(csv(inv.getCurrency())).append('\n');
        sb.append("Is Audited,").append(inv.getIsAudited() != null && inv.getIsAudited() ? "Yes" : "No").append('\n');
        sb.append('\n');

        // Line items section
        sb.append("=== LINE ITEMS ===\n");
        sb.append("Description,HSN Code,Quantity,Unit Price,Tax Rate (%),Total Price\n");
        List<LineItem> items = inv.getLineItems();
        if (items != null && !items.isEmpty()) {
            for (LineItem item : items) {
                sb.append(csv(item.getDescription())).append(',')
                  .append(csv(item.getHsnCode())).append(',')
                  .append(csv(item.getQuantity())).append(',')
                  .append(csv(item.getUnitPrice())).append(',')
                  .append(csv(item.getTaxRate())).append(',')
                  .append(csv(item.getTotalPrice()))
                  .append('\n');
            }
        } else {
            sb.append("No line items\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Audit Log Exports ──────────────────────────────────────────────────────

    /**
     * Generates a CSV byte array for audit logs belonging to an organization,
     * with optional action filter.
     */
    @Transactional(readOnly = true)
    public byte[] exportAuditLogsCsv(Organization org, AuditAction actionFilter) {
        List<AuditLog> logs = fetchAuditLogs(org, actionFilter);
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,Action,User,Entity Type,Field Name,Old Value,New Value\n");
        for (AuditLog log : logs) {
            sb.append(csv(log.getCreatedAt())).append(',')
              .append(csv(log.getAction())).append(',')
              .append(csv(log.getUser() != null ? log.getUser().getEmail() : "")).append(',')
              .append(csv(log.getEntityType())).append(',')
              .append(csv(log.getFieldName())).append(',')
              .append(csv(log.getOldValue())).append(',')
              .append(csv(log.getNewValue()))
              .append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private List<AuditLog> fetchAuditLogs(Organization org, AuditAction actionFilter) {
        Pageable all = PageRequest.of(0, 10_000);
        if (actionFilter != null) {
            return auditLogRepository.findByOrganizationAndAction(org, actionFilter, all).getContent();
        }
        return auditLogRepository.findByOrganization(org, all).getContent();
    }

    // ── CSV Escape Helper ──────────────────────────────────────────────────────

    private String csv(Object value) {
        if (value == null) return "";
        String str = value.toString();
        // RFC 4180 — wrap in quotes if contains comma, newline, or double-quote
        if (str.contains(",") || str.contains("\n") || str.contains("\"")) {
            str = "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
