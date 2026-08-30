package com.scanly.backend.repository;

import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Page<AuditLog> findByDocumentId(UUID documentId, Pageable pageable);

    Page<AuditLog> findByInvoiceId(UUID invoiceId, Pageable pageable);

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    // All logs for an org (via the user's organization), newest first
    @Query("SELECT a FROM AuditLog a WHERE a.user.organization = :org ORDER BY a.createdAt DESC")
    Page<AuditLog> findByOrganization(@Param("org") Organization org, Pageable pageable);

    // Filtered by action type
    @Query("SELECT a FROM AuditLog a WHERE a.user.organization = :org AND a.action = :action ORDER BY a.createdAt DESC")
    Page<AuditLog> findByOrganizationAndAction(
        @Param("org") Organization org,
        @Param("action") AuditAction action,
        Pageable pageable
    );
}
