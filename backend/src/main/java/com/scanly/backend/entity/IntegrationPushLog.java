package com.scanly.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * IntegrationPushLog entity.
 *
 * Records the result of every push operation — how many invoices succeeded,
 * how many failed, and the error details for each failed invoice.
 */
@Entity
@Table(
    name = "integration_push_logs",
    indexes = {
        @Index(name = "idx_push_logs_integration", columnList = "integration_id"),
        @Index(name = "idx_push_logs_pushed_at",   columnList = "pushed_at")
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class IntegrationPushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private IntegrationConfig integration;

    @Column(name = "total_attempted", nullable = false)
    private Integer totalAttempted;

    @Column(name = "total_succeeded", nullable = false)
    private Integer totalSucceeded;

    @Column(name = "total_failed", nullable = false)
    private Integer totalFailed;

    /**
     * JSON array of failed invoice details.
     * Format: [{"invoiceId":"uuid","invoiceNumber":"INV-42","error":"value too long..."}]
     */
    @Column(name = "failed_invoices", columnDefinition = "TEXT")
    private String failedInvoices;

    /**
     * Overall status.
     * SUCCESS   — all pushed
     * PARTIAL   — some failed
     * FAILED    — all failed / connection error
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "pushed_at", nullable = false, updatable = false)
    private Instant pushedAt;
}
