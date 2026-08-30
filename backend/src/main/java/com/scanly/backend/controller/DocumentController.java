package com.scanly.backend.controller;

import com.scanly.backend.dto.UploadResponse;
import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.User;
import com.scanly.backend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document Controller.
 *
 * POST /api/v1/documents/upload  — Upload one or more files (PDF/JPG/PNG)
 * GET  /api/v1/documents         — List all documents for the user's organization
 * GET  /api/v1/documents/{id}    — Get a single document by ID
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocuments(
        @RequestParam("files") List<MultipartFile> files,
        @AuthenticationPrincipal User currentUser
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "NO_FILES", "message", "No files provided"));
        }

        try {
            UploadResponse response = documentService.uploadDocuments(files, currentUser);

            if (response.getTotalFiles() == 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_FILES",
                        "message", "No valid files found. Accepted: PDF, JPG, PNG under 10MB"));
            }

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_UPLOAD", "message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "UPLOAD_FAILED", "message", "Failed to save files: " + e.getMessage()));
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Document>> listDocuments(
        @AuthenticationPrincipal User currentUser
    ) {
        List<Document> docs = documentService.getDocumentsByOrganization(currentUser);
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocument(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        return documentService.getDocumentById(id, currentUser)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
