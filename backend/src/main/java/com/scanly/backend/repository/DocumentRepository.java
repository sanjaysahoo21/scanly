package com.scanly.backend.repository;

import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @EntityGraph(attributePaths = {"uploadedBy"})
    List<Document> findByOrganizationOrderByCreatedAtDesc(Organization organization);

    // Used by DocumentService.getDocumentById() — ensures org-level isolation
    @EntityGraph(attributePaths = {"uploadedBy"})
    Optional<Document> findByIdAndOrganization(UUID id, Organization organization);

    // Used by DashboardService — count docs by status for an org
    long countByOrganizationAndStatus(Organization organization, DocumentStatus status);

    // Total count for an org (all statuses)
    long countByOrganization(Organization organization);

    long countByOrganizationAndFolderId(Organization organization, UUID folderId);

    // 5 most recent documents for the dashboard preview
    @EntityGraph(attributePaths = {"uploadedBy"})
    List<Document> findTop5ByOrganizationOrderByCreatedAtDesc(Organization organization);

    // Count all by organization id (used by Dashboard to avoid lazy-loading Organization object)
    long countByOrganizationId(UUID organizationId);

    // Nullify folder_id for all documents belonging to a folder being deleted
    @Modifying
    @Query("UPDATE Document d SET d.folderId = NULL WHERE d.folderId = :folderId")
    void clearFolderReference(@Param("folderId") UUID folderId);

    // List documents in a specific folder
    @EntityGraph(attributePaths = {"uploadedBy"})
    List<Document> findByOrganizationAndFolderIdOrderByCreatedAtDesc(Organization organization, UUID folderId);
}
