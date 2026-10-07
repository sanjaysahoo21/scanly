package com.scanly.backend.repository;

import com.scanly.backend.entity.InvoiceFolder;
import com.scanly.backend.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceFolderRepository extends JpaRepository<InvoiceFolder, UUID> {

    List<InvoiceFolder> findByOrganizationOrderByCreatedAtDesc(Organization organization);

    Optional<InvoiceFolder> findByIdAndOrganization(UUID id, Organization organization);

    boolean existsByNameIgnoreCaseAndOrganization(String name, Organization organization);
}
