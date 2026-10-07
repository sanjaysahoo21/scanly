package com.scanly.backend.repository;

import com.scanly.backend.entity.IntegrationConfig;
import com.scanly.backend.entity.IntegrationPushLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationPushLogRepository extends JpaRepository<IntegrationPushLog, UUID> {

    /** Most recent push logs for a given integration, newest first. */
    List<IntegrationPushLog> findByIntegrationOrderByPushedAtDesc(IntegrationConfig integration);

    /** Count total pushes for an integration. */
    long countByIntegration(IntegrationConfig integration);
}
