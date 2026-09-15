package com.scanly.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanly.backend.dto.InvoiceResponse;
import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.repository.AuditLogRepository;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ExportService
 *
 * Generates CSV and JSON byte payloads (within @Transactional boundary) for:
 *   - All invoices (with line item summaries) scoped to an organization
 *   - A single invoice with full line items breakdown
 *   - Audit logs scoped to an organization, with optional action filter
 *
 * All methods return byte[] ready for streaming as HTTP response bodies.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // ── Invoice Exports ────────────────────────────────────────────────────────

    /**
     * All invoices for an org → CSV (one row per invoice, line item count summary).
     */
    @Transactional(readOnly = true)
    public byte[] exportAllInvoicesCsv(UUID orgId) {
        List<Invoice> invoices = invoiceRepository
            .findByDocumentOrganizationId(orgId, Pageable.unpaged()).getContent();

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
              .append(Boolean.TRUE.equals(inv.getIsAudited()) ? "Yes" : "No").append(',')
              .append(inv.getLineItems() != null ? inv.getLineItems().size() : 0)
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * All invoices for an org → JSON array.
     */
    @Transactional(readOnly = true)
    public byte[] exportAllInvoicesJson(UUID orgId) throws Exception {
        List<InvoiceResponse> dtos = invoiceRepository
            .findByDocumentOrganizationId(orgId, Pageable.unpaged()).getContent()
            .stream().map(InvoiceResponse::from).toList();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dtos);
    }

    /**
     * Single invoice (with full line items) → CSV.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> exportSingleInvoiceCsv(UUID invoiceId, UUID orgId) {
        return invoiceRepository.findByIdAndDocumentOrganizationId(invoiceId, orgId)
            .map(inv -> {
                StringBuilder sb = new StringBuilder();
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
                sb.append("Is Audited,").append(Boolean.TRUE.equals(inv.getIsAudited()) ? "Yes" : "No").append('\n');
                sb.append('\n');

                sb.append("=== LINE ITEMS ===\n");
                sb.append("Description,HSN Code,Quantity,Unit Price,Tax Rate (%),Total Price\n");
                var items = inv.getLineItems();
                if (items != null && !items.isEmpty()) {
                    for (var item : items) {
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
                return sb.toString().getBytes(StandardCharsets.UTF_8);
            });
    }

    /**
     * Single invoice (with full line items) → JSON.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> exportSingleInvoiceJson(UUID invoiceId, UUID orgId) throws Exception {
        var opt = invoiceRepository.findByIdAndDocumentOrganizationId(invoiceId, orgId);
        if (opt.isEmpty()) return Optional.empty();
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(InvoiceResponse.from(opt.get()));
        return Optional.of(json);
    }

    // ── Audit Log Exports ──────────────────────────────────────────────────────

    /**
     * Audit logs for an org → CSV.
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
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Audit logs for an org → JSON.
     */
    @Transactional(readOnly = true)
    public byte[] exportAuditLogsJson(Organization org, AuditAction actionFilter) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(fetchAuditLogs(org, actionFilter));
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
        // RFC 4180: wrap in quotes if the value contains comma, newline, or double-quote
        if (str.contains(",") || str.contains("\n") || str.contains("\"")) {
            str = "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
