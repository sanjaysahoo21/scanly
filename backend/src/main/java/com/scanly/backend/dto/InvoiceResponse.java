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
    private Boolean hasValidationErrors;
    private String validationIssues;
    private Boolean isDuplicate;
    private UUID duplicateOfId;
    private String duplicateReason;
    private List<LineItemDto> lineItems;

    /** Flat, safe projection of a LineItem — no JPA back-references. */
    @Getter
    @Builder
    public static class LineItemDto {
        private UUID id;
        private String description;
        private String hsnCode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxRate;
        private BigDecimal totalPrice;
        private Instant createdAt;

        public static LineItemDto from(LineItem item) {
            return LineItemDto.builder()
                .id(item.getId())
                .description(item.getDescription())
                .hsnCode(item.getHsnCode())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .taxRate(item.getTaxRate())
                .totalPrice(item.getTotalPrice())
                .createdAt(item.getCreatedAt())
                .build();
        }
    }

    public static InvoiceResponse from(Invoice invoice) {
        return InvoiceResponse.builder()
            .id(invoice.getId()).documentId(invoice.getDocument().getId())
            .invoiceNumber(invoice.getInvoiceNumber()).vendorName(invoice.getVendorName())
            .vendorAddress(invoice.getVendorAddress()).vendorGstin(invoice.getVendorGstin())
            .buyerName(invoice.getBuyerName()).buyerAddress(invoice.getBuyerAddress())
            .buyerGstin(invoice.getBuyerGstin()).invoiceDate(invoice.getInvoiceDate()).dueDate(invoice.getDueDate())
            .subtotal(invoice.getSubtotal()).taxAmount(invoice.getTaxAmount()).discountAmount(invoice.getDiscountAmount())
            .totalAmount(invoice.getTotalAmount()).currency(invoice.getCurrency()).isAudited(invoice.getIsAudited())
            .auditedAt(invoice.getAuditedAt())
            .hasValidationErrors(invoice.getHasValidationErrors())
            .validationIssues(invoice.getValidationIssues())
            .isDuplicate(invoice.getIsDuplicate())
            .duplicateOfId(invoice.getDuplicateOfId())
            .duplicateReason(invoice.getDuplicateReason())
            .lineItems(invoice.getLineItems() == null ? List.of()
                : invoice.getLineItems().stream().map(LineItemDto::from).toList())
            .build();
    }
}
