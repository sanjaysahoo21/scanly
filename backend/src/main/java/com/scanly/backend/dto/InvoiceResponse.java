package com.scanly.backend.dto;

import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.LineItem;
import com.scanly.backend.entity.enums.Currency;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Safe API representation of an invoice; it deliberately omits JPA relations and user data. */
@Getter
@Builder
public class InvoiceResponse {
    private UUID id;
    private UUID documentId;
    private String invoiceNumber;
    private String vendorName;
    private String vendorAddress;
    private String vendorGstin;
    private String buyerName;
    private String buyerAddress;
    private String buyerGstin;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private Currency currency;
    private Boolean isAudited;
    private Instant auditedAt;
    private List<LineItem> lineItems;

    public static InvoiceResponse from(Invoice invoice) {
        return InvoiceResponse.builder()
            .id(invoice.getId()).documentId(invoice.getDocument().getId())
            .invoiceNumber(invoice.getInvoiceNumber()).vendorName(invoice.getVendorName())
            .vendorAddress(invoice.getVendorAddress()).vendorGstin(invoice.getVendorGstin())
            .buyerName(invoice.getBuyerName()).buyerAddress(invoice.getBuyerAddress())
            .buyerGstin(invoice.getBuyerGstin()).invoiceDate(invoice.getInvoiceDate()).dueDate(invoice.getDueDate())
            .subtotal(invoice.getSubtotal()).taxAmount(invoice.getTaxAmount()).discountAmount(invoice.getDiscountAmount())
            .totalAmount(invoice.getTotalAmount()).currency(invoice.getCurrency()).isAudited(invoice.getIsAudited())
            .auditedAt(invoice.getAuditedAt()).lineItems(List.copyOf(invoice.getLineItems())).build();
    }
}
