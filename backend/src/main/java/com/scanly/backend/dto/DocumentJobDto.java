package com.scanly.backend.dto;

import com.scanly.backend.entity.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents one uploaded document job in the upload response.
 */
@Data
@Builder
public class DocumentJobDto {
    private UUID jobId;
    private String fileName;
    private DocumentStatus status;
    private Instant uploadedAt;
}
