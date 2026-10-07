package com.scanly.backend.service;

import com.scanly.backend.dto.DocumentJobDto;
import com.scanly.backend.dto.UploadResponse;
import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.InvoiceFolder;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.entity.enums.DocumentStatus;
import com.scanly.backend.entity.enums.FileType;
import com.scanly.backend.repository.DocumentRepository;
import com.scanly.backend.repository.InvoiceFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Document Service.
 *
 * Handles the file upload flow:
 *   1. Validate each file (type + size)
 *   2. Save file to disk (./uploads/<orgId>/<uuid>_<filename>)
 *   3. Create a Document record in the DB with status PENDING
 *   4. Return a list of job IDs for the frontend to poll
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;
    private final AuditService auditService;
    private final InvoiceFolderRepository folderRepository;

    @Value("${scanly.upload-dir}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final int MAX_FILES_PER_REQUEST = 50; // supports folder uploads with many files

    public UploadResponse uploadDocuments(List<MultipartFile> files, User currentUser, UUID folderId) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new IllegalArgumentException("A maximum of " + MAX_FILES_PER_REQUEST + " files may be uploaded at once");
        }
        Organization org = currentUser.getOrganization();
        InvoiceFolder destinationFolder = null;
        if (folderId != null) {
            destinationFolder = folderRepository.findByIdAndOrganization(folderId, org)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        }
        List<DocumentJobDto> jobs = new ArrayList<>();

        // Create org-specific upload directory (always resolve to absolute to avoid
        // normalization mismatches in the path-traversal check below)
        Path orgUploadPath = Paths.get(uploadDir, org.getId().toString())
                .toAbsolutePath().normalize();
        Files.createDirectories(orgUploadPath);

        for (MultipartFile file : files) {
            // Validate file
            if (file.isEmpty()) continue;

            String contentType = file.getContentType();
            FileType fileType = detectFileType(file);
            if (fileType == null) {
                log.warn("Rejected file '{}' — unsupported type: {}", file.getOriginalFilename(), contentType);
                continue;
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                log.warn("Rejected file '{}' — exceeds 10MB limit", file.getOriginalFilename());
                continue;
            }

            // Generate unique file name preserving original extension
            String originalName = safeFileName(file.getOriginalFilename());
            String ext = originalExtension(originalName, fileType);
            String storedFileName = UUID.randomUUID() + ext;
            Path filePath = orgUploadPath.resolve(storedFileName).toAbsolutePath().normalize();
            if (!filePath.startsWith(orgUploadPath)) {
                throw new IOException("Invalid upload path: potential path traversal detected");
            }

            // Save file to disk
            try (var input = file.getInputStream()) {
                Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Saved file: {}", filePath);

            // Determine FileType enum from magic bytes (already detected above)

            // Create Document record in DB with status PENDING
            Document document = Document.builder()
                .organization(org)
                .uploadedBy(currentUser)
                .fileName(originalName)
                .filePath(filePath.toString())
                .fileType(fileType)
                .fileSizeBytes(file.getSize())
                .status(DocumentStatus.PENDING)
                .folderId(folderId)
                .build();

            document = documentRepository.saveAndFlush(document);

            // Trigger async AI/OCR processing in background with ID
            processingService.processDocument(document.getId());
            log.info("Queued document {} for AI processing", document.getId());

            // Write an UPLOAD audit log so the audit trail is populated from the start
            try {
                auditService.recordDocument(document, currentUser, AuditAction.UPLOAD,
                    "status", null, DocumentStatus.PENDING.name());
            } catch (Exception ae) {
                log.warn("Audit log write failed for document {}: {}", document.getId(), ae.getMessage());
            }

            jobs.add(DocumentJobDto.builder()
                .jobId(document.getId())
                .fileName(originalName)
                .status(DocumentStatus.PENDING)
                .uploadedAt(Instant.now())
                .build());
        }

        // Store a verified count once per batch.  This avoids accepting another
        // organization's folder ID and prevents a partial batch from corrupting
        // the count shown in the UI.
        if (destinationFolder != null) {
            destinationFolder.setDocumentCount((int) documentRepository.countByOrganizationAndFolderId(org, folderId));
            folderRepository.save(destinationFolder);
        }

        return UploadResponse.builder()
            .message("Documents accepted for processing")
            .totalFiles(jobs.size())
            .jobs(jobs)
            .build();
    }

    /**
     * Trigger reprocessing for an existing document.
     */
    public boolean reprocessDocument(UUID documentId, User currentUser) {
        Optional<Document> docOpt = getDocumentById(documentId, currentUser);
        if (docOpt.isPresent()) {
            processingService.processDocument(documentId);
            return true;
        }
        return false;
    }

    /**
     * Get all documents for the logged-in user's organization, newest first.
     * If folderId is provided, only returns documents in that folder.
     */
    public List<Document> getDocumentsByOrganization(User currentUser, UUID folderId) {
        if (folderId != null) {
            return documentRepository.findByOrganizationAndFolderIdOrderByCreatedAtDesc(
                currentUser.getOrganization(), folderId);
        }
        return documentRepository.findByOrganizationOrderByCreatedAtDesc(currentUser.getOrganization());
    }

    /**
     * Get a single document by ID — only if it belongs to the user's organization.
     */
    public Optional<Document> getDocumentById(UUID id, User currentUser) {
        return documentRepository.findByIdAndOrganization(id, currentUser.getOrganization());
    }

    /**
     * Delete a document permanently.
     * Removes the DB record, its associated invoice/line-items (cascade), and the file from disk.
     * Returns Optional.empty() if not found or the doc doesn't belong to the org.
     */
    @Transactional
    public Optional<String> deleteDocument(UUID id, User currentUser) {
        return documentRepository.findByIdAndOrganization(id, currentUser.getOrganization()).map(doc -> {
            // Delete file from disk
            try {
                Path filePath = Paths.get(doc.getFilePath());
                Files.deleteIfExists(filePath);
            } catch (Exception e) {
                log.warn("Could not delete file for document {}: {}", id, e.getMessage());
            }
            // If the document was in a folder, refresh that folder's count
            if (doc.getFolderId() != null) {
                folderRepository.findById(doc.getFolderId()).ifPresent(folder -> {
                    long newCount = documentRepository.countByOrganizationAndFolderId(
                        currentUser.getOrganization(), folder.getId()) - 1;
                    folder.setDocumentCount((int) Math.max(0, newCount));
                    folderRepository.save(folder);
                });
            }
            documentRepository.delete(doc);
            log.info("Document {} deleted by {}", id, currentUser.getEmail());
            return "Document deleted successfully";
        });
    }


    /**
     * Detect file type using the browser-supplied content type and file extension.
     * For PDFs we additionally verify the magic bytes (%PDF-) to prevent spoofing.
     * For images the browser content type is trusted — browsers reliably declare
     * image/jpeg, image/png, image/webp for real image files.
     *
     * Returns null for unsupported types.
     */
    private FileType detectFileType(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) return null;

        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        // ── PDF ────────────────────────────────────────────────────────────────
        boolean looksLikePdf = ct.contains("pdf") || name.endsWith(".pdf");
        if (looksLikePdf) {
            // Verify magic bytes so a renamed image can't sneak through as PDF
            try (var input = file.getInputStream()) {
                byte[] header = input.readNBytes(5);
                if (header.length == 5
                        && header[0] == '%' && header[1] == 'P'
                        && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
                    return FileType.PDF;
                }
            }
            return null; // content type said PDF but magic bytes don't match
        }

        // ── Images — trust the browser's content type and/or file extension ──
        boolean looksLikeImage = ct.startsWith("image/jpeg")
                || ct.startsWith("image/jpg")
                || ct.startsWith("image/png")
                || ct.startsWith("image/webp")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".webp");

        if (looksLikeImage) return FileType.IMAGE;

        return null;
    }

    /**
     * Returns the file extension to use for storage based on detected type.
     */
    private String originalExtension(String originalName, FileType fileType) {
        if (fileType == FileType.PDF) return ".pdf";
        // For images, keep the original extension so the OS/viewer knows the format
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            String ext = originalName.substring(dot).toLowerCase();
            if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp")) {
                return ext.equals(".jpg") ? ".jpg" : ext;
            }
        }
        return ".jpg"; // fallback
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "document.pdf";
        String name = Paths.get(originalName).getFileName().toString();
        return name.replaceAll("[\\r\\n\\x00]", "_");
    }
}
