package com.scanly.backend.controller;

import com.scanly.backend.dto.FolderRequest;
import com.scanly.backend.dto.FolderResponse;
import com.scanly.backend.entity.InvoiceFolder;
import com.scanly.backend.entity.User;
import com.scanly.backend.repository.DocumentRepository;
import com.scanly.backend.repository.InvoiceFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Folder Controller.
 *
 * GET    /api/v1/folders              — List all folders for the current org
 * POST   /api/v1/folders              — Create a new folder
 * PUT    /api/v1/folders/{id}         — Rename / update a folder
 * DELETE /api/v1/folders/{id}         — Delete a folder (documents become unorganized)
 * PUT    /api/v1/folders/{id}/documents/{docId} — Move a document into a folder
 * DELETE /api/v1/folders/{id}/documents/{docId} — Remove a document from a folder
 */
@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
@Slf4j
public class FolderController {

    private final InvoiceFolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    // ── List folders ──────────────────────────────────────────────────────────

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<FolderResponse>> list(@AuthenticationPrincipal User currentUser) {
        List<FolderResponse> folders = folderRepository
            .findByOrganizationOrderByCreatedAtDesc(currentUser.getOrganization())
            .stream()
            .map(folder -> toResponse(folder, currentUser))
            .toList();
        return ResponseEntity.ok(folders);
    }

    // ── Get single folder ─────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return folderRepository.findByIdAndOrganization(id, currentUser.getOrganization())
            .<ResponseEntity<?>>map(f -> ResponseEntity.ok(toResponse(f, currentUser)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Create folder ─────────────────────────────────────────────────────────

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody FolderRequest req,
                                    @AuthenticationPrincipal User currentUser) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_NAME", "message", "Folder name is required"));
        }
        if (req.getName().length() > 255) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "NAME_TOO_LONG", "message", "Folder name must be 255 characters or less"));
        }
        if (folderRepository.existsByNameIgnoreCaseAndOrganization(req.getName().trim(), currentUser.getOrganization())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "DUPLICATE_NAME", "message", "A folder with this name already exists"));
        }

        String color = (req.getColor() != null && req.getColor().matches("^#[0-9A-Fa-f]{6}$"))
            ? req.getColor() : "#6366f1";

        InvoiceFolder folder = InvoiceFolder.builder()
            .organization(currentUser.getOrganization())
            .createdBy(currentUser)
            .name(req.getName().trim())
            .description(req.getDescription())
            .color(color)
            .build();

        folder = folderRepository.save(folder);
        log.info("Folder '{}' created by {} (org={})", folder.getName(),
            currentUser.getEmail(), currentUser.getOrganization().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(folder, currentUser));
    }

    // ── Update folder ─────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody FolderRequest req,
                                    @AuthenticationPrincipal User currentUser) {
        return folderRepository.findByIdAndOrganization(id, currentUser.getOrganization())
            .<ResponseEntity<?>>map(folder -> {
                if (req.getName() != null) {
                    if (req.getName().isBlank()) {
                        return ResponseEntity.badRequest()
                            .body(Map.of("error", "INVALID_NAME", "message", "Folder name is required"));
                    }
                    if (req.getName().trim().length() > 255) {
                        return ResponseEntity.badRequest()
                            .body(Map.of("error", "NAME_TOO_LONG", "message", "Folder name must be 255 characters or less"));
                    }
                    // Check name collision (excluding self)
                    boolean nameConflict = folderRepository
                        .existsByNameIgnoreCaseAndOrganization(req.getName().trim(), currentUser.getOrganization())
                        && !folder.getName().equalsIgnoreCase(req.getName().trim());
                    if (nameConflict) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "DUPLICATE_NAME",
                                "message", "A folder with this name already exists"));
                    }
                    folder.setName(req.getName().trim());
                }
                if (req.getDescription() != null) {
                    folder.setDescription(req.getDescription());
                }
                if (req.getColor() != null && req.getColor().matches("^#[0-9A-Fa-f]{6}$")) {
                    folder.setColor(req.getColor());
                }
                return ResponseEntity.ok(toResponse(folderRepository.save(folder), currentUser));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Delete folder ─────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable UUID id,
                                    @AuthenticationPrincipal User currentUser) {
        return folderRepository.findByIdAndOrganization(id, currentUser.getOrganization())
            .<ResponseEntity<?>>map(folder -> {
                // Un-file all documents in this folder before deleting
                documentRepository.clearFolderReference(id);
                folderRepository.delete(folder);
                log.info("Folder '{}' deleted (org={})", folder.getName(),
                    currentUser.getOrganization().getId());
                return ResponseEntity.noContent().build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Move document into folder ─────────────────────────────────────────────

    @PutMapping("/{folderId}/documents/{docId}")
    @Transactional
    public ResponseEntity<?> addDocument(@PathVariable UUID folderId,
                                         @PathVariable UUID docId,
                                         @AuthenticationPrincipal User currentUser) {
        var folderOpt = folderRepository.findByIdAndOrganization(folderId, currentUser.getOrganization());
        if (folderOpt.isEmpty()) return ResponseEntity.notFound().build();

        var docOpt = documentRepository.findByIdAndOrganization(docId, currentUser.getOrganization());
        if (docOpt.isEmpty()) return ResponseEntity.notFound().build();

        var doc = docOpt.get();
        UUID previousFolder = doc.getFolderId();

        // Decrement previous folder count
        doc.setFolderId(folderId);
        documentRepository.save(doc);

        if (!folderId.equals(previousFolder)) {
            refreshDocumentCount(folderOpt.get(), currentUser);
            if (previousFolder != null) {
                folderRepository.findByIdAndOrganization(previousFolder, currentUser.getOrganization())
                    .ifPresent(previous -> refreshDocumentCount(previous, currentUser));
            }
        }

        return ResponseEntity.ok(Map.of("message", "Document moved to folder"));
    }

    // ── Remove document from folder ───────────────────────────────────────────

    @DeleteMapping("/{folderId}/documents/{docId}")
    @Transactional
    public ResponseEntity<?> removeDocument(@PathVariable UUID folderId,
                                            @PathVariable UUID docId,
                                            @AuthenticationPrincipal User currentUser) {
        var folderOpt = folderRepository.findByIdAndOrganization(folderId, currentUser.getOrganization());
        if (folderOpt.isEmpty()) return ResponseEntity.notFound().build();

        var docOpt = documentRepository.findByIdAndOrganization(docId, currentUser.getOrganization());
        if (docOpt.isEmpty()) return ResponseEntity.notFound().build();

        var doc = docOpt.get();
        if (folderId.equals(doc.getFolderId())) {
            doc.setFolderId(null);
            documentRepository.save(doc);
            refreshDocumentCount(folderOpt.get(), currentUser);
        }

        return ResponseEntity.ok(Map.of("message", "Document removed from folder"));
    }

    private FolderResponse toResponse(InvoiceFolder folder, User currentUser) {
        folder.setDocumentCount((int) documentRepository.countByOrganizationAndFolderId(
            currentUser.getOrganization(), folder.getId()));
        return FolderResponse.from(folder);
    }

    private void refreshDocumentCount(InvoiceFolder folder, User currentUser) {
        folder.setDocumentCount((int) documentRepository.countByOrganizationAndFolderId(
            currentUser.getOrganization(), folder.getId()));
        folderRepository.save(folder);
    }
}
