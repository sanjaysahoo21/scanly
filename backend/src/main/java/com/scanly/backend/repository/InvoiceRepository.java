package com.scanly.backend.repository;

import com.scanly.backend.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    @EntityGraph(attributePaths = {"lineItems"})
    Optional<Invoice> findByDocumentId(UUID documentId);

    @EntityGraph(attributePaths = {"document", "lineItems"})
    Page<Invoice> findByDocumentOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"document", "lineItems"})
    Optional<Invoice> findByIdAndDocumentOrganizationId(UUID id, UUID organizationId);

    /** Batch lookup — used for selective export. */
    @EntityGraph(attributePaths = {"document", "lineItems"})
    List<Invoice> findByIdInAndDocumentOrganizationId(Collection<UUID> ids, UUID organizationId);

    long countByIsAudited(boolean isAudited);

    // ── Duplicate detection ───────────────────────────────────────────────────

    /**
     * Find invoices in the same org with the same invoice number (excluding the given ID).
     */
    @Query("SELECT i FROM Invoice i JOIN i.document d " +
           "WHERE d.organization.id = :orgId " +
           "AND LOWER(i.invoiceNumber) = LOWER(:invoiceNumber) " +
           "AND i.id <> :excludeId")
    List<Invoice> findSameInvoiceNumber(@Param("orgId") UUID orgId,
                                        @Param("invoiceNumber") String invoiceNumber,
                                        @Param("excludeId") UUID excludeId);

    /**
     * Find invoices in the same org with matching vendor name, total amount, and date (excluding the given ID).
     */
    @Query("SELECT i FROM Invoice i JOIN i.document d " +
           "WHERE d.organization.id = :orgId " +
           "AND LOWER(i.vendorName) = LOWER(:vendorName) " +
           "AND i.totalAmount = :totalAmount " +
           "AND i.invoiceDate = :invoiceDate " +
           "AND i.id <> :excludeId")
    List<Invoice> findSameVendorAmountDate(@Param("orgId") UUID orgId,
                                           @Param("vendorName") String vendorName,
                                           @Param("totalAmount") BigDecimal totalAmount,
                                           @Param("invoiceDate") LocalDate invoiceDate,
                                           @Param("excludeId") UUID excludeId);
}
