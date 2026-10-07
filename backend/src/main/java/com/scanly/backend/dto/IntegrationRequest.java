package com.scanly.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for creating or updating an IntegrationConfig.
 *
 * The raw password is sent here but NEVER stored — it is encrypted by
 * CryptoService before being written to the database.
 */
@Getter
@Setter
public class IntegrationRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 200)
    private String label;

    /**
     * Database type. Must be one of: postgresql, mysql
     * (mongodb and sheets will be added in future phases)
     */
    @NotBlank(message = "Database type is required")
    @Pattern(regexp = "postgresql|mysql", message = "Supported types: postgresql, mysql")
    private String dbType;

    @NotBlank(message = "Host is required")
    @Size(max = 500)
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1,     message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    @NotBlank(message = "Database name is required")
    @Size(max = 200)
    private String databaseName;

    @NotBlank(message = "Username is required")
    @Size(max = 200)
    private String username;

    /**
     * Required when creating a new integration.
     * Optional when updating — if blank, the existing password is kept.
     */
    private String password;

    private boolean useSsl = false;

    /**
     * Tier selection:
     * - null / blank → Tier 1 (auto-create scanly_invoices table)
     * - a table name  → Tier 2 (push to this existing table)
     */
    @Size(max = 200)
    private String targetTable;

    /** Push mode: "best_effort" (default) or "all_or_nothing" */
    @Pattern(regexp = "best_effort|all_or_nothing", message = "pushMode must be best_effort or all_or_nothing")
    private String pushMode = "best_effort";

    /** Line items strategy: "skip" (default), "json", or "flatten" */
    @Pattern(regexp = "skip|json|flatten", message = "lineItemsStrategy must be skip, json, or flatten")
    private String lineItemsStrategy = "skip";
}
