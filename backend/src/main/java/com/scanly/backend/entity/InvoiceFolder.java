package com.scanly.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * InvoiceFolder entity.
 *
 * Represents a user-created folder used to categorize documents/invoices.
 * Folders are scoped to an organization.
 */
@Entity
@Table(name = "invoice_folders", indexes = {
    @Index(name = "idx_invoice_folders_org", columnList = "organization_id"),
    @Index(name = "idx_invoice_folders_created_by", columnList = "created_by")
}, uniqueConstraints = @UniqueConstraint(name = "uk_invoice_folders_org_name", columnNames = {"organization_id", "name"}))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Hex color code for UI display (e.g. "#6366f1") */
    @Column(name = "color", length = 10)
    @Builder.Default
    private String color = "#6366f1";

    /** How many documents are in this folder (denormalized for fast display). */
    @Column(name = "document_count", nullable = false)
    @Builder.Default
    private Integer documentCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
