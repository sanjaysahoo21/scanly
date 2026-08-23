package com.scanly.backend.repository;

import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Document> findByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status);

    // Used by DocumentService.getDocumentsByOrganization()
    List<Document> findByOrganizationOrderByCreatedAtDesc(Organization organization);

    // Used by DocumentService.getDocumentById() — ensures org-level isolation
    Optional<Document> findByIdAndOrganization(UUID id, Organization organization);

    // Used by DashboardService — count docs by status for an org
    long countByOrganizationAndStatus(Organization organization, DocumentStatus status);

    // Total count for an org (all statuses)
    long countByOrganization(Organization organization);

    // 5 most recent documents for the dashboard preview
    List<Document> findTop5ByOrganizationOrderByCreatedAtDesc(Organization organization);
}
