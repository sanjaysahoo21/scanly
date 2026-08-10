package com.scanly.backend.repository;

import com.scanly.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByDocumentId(UUID documentId, Pageable pageable);

    Page<AuditLog> findByInvoiceId(UUID invoiceId, Pageable pageable);

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);
}
