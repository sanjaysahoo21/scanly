package com.scanly.backend.repository;

import com.scanly.backend.entity.IntegrationConfig;
import com.scanly.backend.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, UUID> {

    /** All integrations for the given organization (active or not). */
    List<IntegrationConfig> findByOrganizationOrderByCreatedAtDesc(Organization organization);

    /** Find a specific integration by ID that belongs to this organization. */
    Optional<IntegrationConfig> findByIdAndOrganization(UUID id, Organization organization);
}
