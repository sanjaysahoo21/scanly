package com.scanly.backend.dto;

import com.scanly.backend.entity.InvoiceFolder;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for InvoiceFolder — safe for serialization (no lazy associations).
 */
@Data
@Builder
public class FolderResponse {

    private UUID id;
    private String name;
    private String description;
    private String color;
    private Integer documentCount;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;

    public static FolderResponse from(InvoiceFolder folder) {
        return FolderResponse.builder()
            .id(folder.getId())
            .name(folder.getName())
            .description(folder.getDescription())
            .color(folder.getColor())
            .documentCount(folder.getDocumentCount())
            .createdByName(folder.getCreatedBy() != null ? folder.getCreatedBy().getFullName() : null)
            .createdAt(folder.getCreatedAt())
            .updatedAt(folder.getUpdatedAt())
            .build();
    }
}
