package com.scanly.backend.repository;

import com.scanly.backend.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByDocumentId(UUID documentId);

    Page<Invoice> findByDocumentOrganizationId(UUID organizationId, Pageable pageable);

    long countByIsAudited(boolean isAudited);
}
