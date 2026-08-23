package com.scanly.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * LineItem entity.
 *
 * Represents a single item/row on an invoice.
 * Each invoice can have many line items.
 */
@Entity
@Table(name = "line_items", indexes = {
    @Index(name = "idx_line_items_invoice", columnList = "invoice_id")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "invoice"})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
