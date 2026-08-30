package com.scanly.backend.service;

import com.scanly.backend.dto.DocumentJobDto;
import com.scanly.backend.dto.UploadResponse;
import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.DocumentStatus;
import com.scanly.backend.entity.enums.FileType;
import com.scanly.backend.repository.DocumentRepository;
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

    @Value("${scanly.upload-dir}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final int MAX_FILES_PER_REQUEST = 10;

    public UploadResponse uploadDocuments(List<MultipartFile> files, User currentUser) throws IOException {
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new IllegalArgumentException("A maximum of " + MAX_FILES_PER_REQUEST + " files may be uploaded at once");
        }
        Organization org = currentUser.getOrganization();
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
            if (!isPdf(file)) {
                log.warn("Rejected file '{}' — unsupported type: {}", file.getOriginalFilename(), contentType);
                continue;
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                log.warn("Rejected file '{}' — exceeds 10MB limit", file.getOriginalFilename());
                continue;
            }

            // Generate unique file name to avoid collisions
            String originalName = safeFileName(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID() + ".pdf";
            Path filePath = orgUploadPath.resolve(storedFileName).toAbsolutePath().normalize();
            if (!filePath.startsWith(orgUploadPath)) {
                throw new IOException("Invalid upload path: potential path traversal detected");
            }

            // Save file to disk
            try (var input = file.getInputStream()) {
                Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Saved file: {}", filePath);

            // Determine FileType enum from content type
            FileType fileType = FileType.PDF;

            // Create Document record in DB with status PENDING
            Document document = Document.builder()
                .organization(org)
                .uploadedBy(currentUser)
                .fileName(originalName)
                .filePath(filePath.toString())
                .fileType(fileType)
                .fileSizeBytes(file.getSize())
                .status(DocumentStatus.PENDING)
                .build();

            document = documentRepository.saveAndFlush(document);

            // Trigger async AI/OCR processing in background with ID
            processingService.processDocument(document.getId());
            log.info("Queued document {} for AI processing", document.getId());

            jobs.add(DocumentJobDto.builder()
                .jobId(document.getId())
                .fileName(originalName)
                .status(DocumentStatus.PENDING)
                .uploadedAt(Instant.now())
                .build());
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
     */
    public List<Document> getDocumentsByOrganization(User currentUser) {
        return documentRepository.findByOrganizationOrderByCreatedAtDesc(currentUser.getOrganization());
    }

    /**
     * Get a single document by ID — only if it belongs to the user's organization.
     */
    public Optional<Document> getDocumentById(UUID id, User currentUser) {
        return documentRepository.findByIdAndOrganization(id, currentUser.getOrganization());
    }

    private boolean isPdf(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) return false;
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            return header.length == 5 && header[0] == '%' && header[1] == 'P'
                && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
        }
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "document.pdf";
        String name = Paths.get(originalName).getFileName().toString();
        return name.replaceAll("[\\r\\n\\x00]", "_");
    }
}
