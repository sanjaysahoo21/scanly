package com.scanly.backend.repository;

import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Document> findByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status);
}
