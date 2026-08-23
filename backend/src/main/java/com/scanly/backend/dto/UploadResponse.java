package com.scanly.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response body for POST /api/v1/documents/upload
 */
@Data
@Builder
public class UploadResponse {
    private String message;
    private int totalFiles;
    private List<DocumentJobDto> jobs;
}
