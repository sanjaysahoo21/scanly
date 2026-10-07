package com.scanly.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * IntegrationConfig entity.
 *
 * Stores a user-configured database connection for pushing extracted invoice
 * data into the user's own database (PostgreSQL, MySQL, MongoDB, etc.).
 *
 * Passwords are stored AES-256-GCM encrypted — never in plaintext.
 */
@Entity
@Table(
    name = "integration_configs",
    indexes = {
        @Index(name = "idx_integration_configs_org", columnList = "organization_id"),
        @Index(name = "idx_integration_configs_created_by", columnList = "created_by")
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class IntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Human-readable label (e.g. "Company ERP", "Analytics DB"). */
    @Column(nullable = false, length = 200)
    private String label;

    /**
     * Database type identifier.
     * Allowed: "postgresql", "mysql", "mongodb"
     */
    @Column(name = "db_type", nullable = false, length = 30)
    private String dbType;

    @Column(nullable = false, length = 500)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "database_name", nullable = false, length = 200)
    private String databaseName;

    @Column(nullable = false, length = 200)
    private String username;

    /**
     * Password encrypted with AES-256-GCM.
     * Stored as Base64(IV + ciphertext).
     * Never returned in API responses.
     */
    @Column(name = "encrypted_password", nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    /**
     * Whether to use SSL/TLS for the connection.
     * For PostgreSQL this adds sslmode=require.
     * For MySQL this adds useSSL=true.
     */
    @Column(name = "use_ssl", nullable = false)
    @Builder.Default
    private Boolean useSsl = false;

    /**
     * Tier 1 (Auto-create): null — we create our own table (scanly_invoices).
     * Tier 2 (Existing table): set to the user's target table name.
     */
    @Column(name = "target_table", length = 200)
    private String targetTable;

    /**
     * JSON object mapping our field names → their column names.
     * Only relevant for Tier 2.
     * Example: {"invoiceNumber":"bill_id","vendorName":"party_name"}
     */
    @Column(name = "field_mapping", columnDefinition = "TEXT")
    private String fieldMapping;

    /**
     * Strategy for line items in SQL targets.
     * Options: "skip" | "json" | "flatten"
     */
    @Column(name = "line_items_strategy", length = 20)
    @Builder.Default
    private String lineItemsStrategy = "skip";

    /**
     * Push mode.
     * Options: "best_effort" | "all_or_nothing"
     */
    @Column(name = "push_mode", length = 20)
    @Builder.Default
    private String pushMode = "best_effort";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
