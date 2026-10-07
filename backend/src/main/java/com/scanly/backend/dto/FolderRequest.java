package com.scanly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating or updating an InvoiceFolder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderRequest {

    /** Folder display name (required). */
    private String name;

    /** Optional short description. */
    private String description;

    /** Hex color string for folder icon, e.g. "#6366f1". Defaults to primary accent. */
    private String color;
}
