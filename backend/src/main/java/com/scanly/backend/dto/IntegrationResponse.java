package com.scanly.backend.dto;

import com.scanly.backend.entity.IntegrationConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe API response for IntegrationConfig.
 * Never includes the encrypted password.
 */
@Getter
@Builder
public class IntegrationResponse {

    private UUID id;
    private String label;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private Boolean useSsl;
    private String targetTable;      // null = Tier 1 (auto-create)
    private String lineItemsStrategy;
    private String pushMode;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    /** Tier description derived from targetTable. */
    public String getTier() {
        return (targetTable == null || targetTable.isBlank()) ? "AUTO_CREATE" : "EXISTING_TABLE";
    }

    public static IntegrationResponse from(IntegrationConfig cfg) {
        return IntegrationResponse.builder()
            .id(cfg.getId())
            .label(cfg.getLabel())
            .dbType(cfg.getDbType())
            .host(cfg.getHost())
            .port(cfg.getPort())
            .databaseName(cfg.getDatabaseName())
            .username(cfg.getUsername())
            .useSsl(cfg.getUseSsl())
            .targetTable(cfg.getTargetTable())
            .lineItemsStrategy(cfg.getLineItemsStrategy())
            .pushMode(cfg.getPushMode())
            .isActive(cfg.getIsActive())
            .createdAt(cfg.getCreatedAt())
            .updatedAt(cfg.getUpdatedAt())
            .build();
    }
}
