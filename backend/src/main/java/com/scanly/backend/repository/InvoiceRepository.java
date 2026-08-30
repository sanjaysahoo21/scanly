package com.scanly.backend.repository;

import com.scanly.backend.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"lineItems"})
    Optional<Invoice> findByDocumentId(UUID documentId);

    @EntityGraph(attributePaths = {"document", "lineItems"})
    Page<Invoice> findByDocumentOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"document", "lineItems"})
    Optional<Invoice> findByIdAndDocumentOrganizationId(UUID id, UUID organizationId);

    long countByIsAudited(boolean isAudited);
}
