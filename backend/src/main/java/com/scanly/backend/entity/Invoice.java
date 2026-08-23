package com.scanly.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.scanly.backend.entity.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Invoice entity.
 *
 * Contains the structured data extracted from a document by the AI parser.
 * Has a 1:1 relationship with Document and a 1:N relationship with LineItems.
 */
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoices_document", columnList = "document_id"),
    @Index(name = "idx_invoices_vendor", columnList = "vendor_name"),
    @Index(name = "idx_invoices_date", columnList = "invoice_date"),
    @Index(name = "idx_invoices_audited", columnList = "is_audited")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "document", "auditedBy"})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "vendor_name", length = 500)
    private String vendorName;

    @Column(name = "vendor_address", columnDefinition = "TEXT")
    private String vendorAddress;

    @Column(name = "vendor_gstin", length = 20)
    private String vendorGstin;

    @Column(name = "buyer_name", length = 500)
    private String buyerName;

    @Column(name = "buyer_address", columnDefinition = "TEXT")
    private String buyerAddress;

    @Column(name = "buyer_gstin", length = 20)
    private String buyerGstin;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    @Builder.Default
    private Currency currency = Currency.INR;

    @Column(name = "is_audited", nullable = false)
    @Builder.Default
    private Boolean isAudited = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audited_by")
    private User auditedBy;

    @Column(name = "audited_at")
    private Instant auditedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
