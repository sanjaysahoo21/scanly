package com.scanly.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * GroqAiService
 *
 * Extracts document text using Apache PDFBox, then uses Groq's high-speed
 * AI LLM to parse and structure invoice fields into standardized JSON.
 */
@Service
@Slf4j
public class GroqAiService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String model;

    @Value("${groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are an expert financial document intelligence system.
        Analyze the provided document text and extract structured invoice information into strict JSON.
        
        Output MUST be a valid JSON object matching this schema:
        {
          "invoiceNumber": "string or null",
          "vendorName": "string or null",
          "vendorAddress": "string or null",
          "vendorGstin": "string or null",
          "buyerName": "string or null",
          "buyerAddress": "string or null",
          "buyerGstin": "string or null",
          "invoiceDate": "YYYY-MM-DD or null",
          "dueDate": "YYYY-MM-DD or null",
          "subtotal": number or null,
          "taxAmount": number or null,
          "discountAmount": number or null,
          "totalAmount": number or null,
          "currency": "INR",
          "lineItems": [
            {
              "description": "string",
              "hsnCode": "string or null",
              "quantity": number,
              "unitPrice": number,
              "taxRate": number or null,
              "totalPrice": number
            }
          ]
        }
        
        Guidelines:
        - Output ONLY pure JSON.
        - Normalize currency codes (default to INR if symbols like ₹ or Rs are found).
        - Format dates as YYYY-MM-DD.
        - Ensure numerical values are parsed without currency symbols or commas.
        """;

    /**
     * Extract invoice text and structure it via Groq AI.
     */
    public String extractAndStructureInvoice(Path filePath, String fileName) throws IOException {
        String documentText = extractRawText(filePath, fileName);
        final int maxCharacters = 100_000;
        if (documentText.length() > maxCharacters) {
            documentText = documentText.substring(0, maxCharacters);
            log.warn("Truncated extracted text from {} to {} characters", fileName, maxCharacters);
        }
        log.info("Extracted {} characters of raw text from {}", documentText.length(), fileName);

        if (documentText.isBlank()) {
            documentText = "Document name: " + fileName + " (Scanned or image file without digital text layer)";
        }

        // Build Groq OpenAI-compatible chat payload
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "Document Text:\n\n" + documentText)
            ),
            "response_format", Map.of("type", "json_object"),
            "temperature", 0.1
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.debug("Sending request to Groq API model: {}", model);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        // Parse response: choices[0].message.content
        JsonNode root = objectMapper.readTree(response.getBody());
        String structuredJson = root
            .path("choices").get(0)
            .path("message")
            .path("content")
            .asText();

        return structuredJson.trim();
    }

    /**
     * Process a scanned image or receipt using Tesseract OCR to extract text,
     * then pass that text to the standard LLM structuring pipeline.
     *
     * This avoids the need for a vision-capable LLM — Tesseract is purpose-built
     * for document/receipt OCR and gives higher accuracy on structured layouts.
     *
     * Requires: Tesseract OCR installed on the system (in PATH).
     *   Windows: winget install UB-Mannheim.TesseractOCR
     *   Ubuntu:  sudo apt install tesseract-ocr
     */
    public String extractAndStructureImage(Path filePath, String fileName) throws Exception {
        log.info("Extracting text from image {} via Tesseract OCR", fileName);

        String ocrText;
        try {
            net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();

            // Look for tessdata in standard Windows install locations
            String tessdata = findTessdata();
            if (tessdata != null) {
                tesseract.setDatapath(tessdata);
            }
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(6); // PSM_ASSUME_UNIFORM_BLOCK — good for invoices

            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(filePath.toFile());
            if (img == null) {
                throw new IOException("Could not read image file: " + fileName);
            }

            ocrText = tesseract.doOCR(img).trim();
            log.info("Tesseract extracted {} characters from {}", ocrText.length(), fileName);
        } catch (Exception e) {
            log.error("Tesseract OCR failed for {}: {}", fileName, e.getMessage());
            throw new IOException("Image OCR failed: " + e.getMessage()
                + ". Ensure Tesseract is installed: winget install UB-Mannheim.TesseractOCR", e);
        }

        if (ocrText.isBlank()) {
            ocrText = "Image file: " + fileName + " (no text could be extracted by OCR)";
        }

        // Reuse the existing LLM structuring pipeline with the OCR'd text
        return extractAndStructureInvoice(filePath, fileName + "_ocr_bypass", ocrText);
    }

    /**
     * Overload that accepts pre-extracted text (used by image OCR path).
     */
    private String extractAndStructureInvoice(Path ignored, String logName, String documentText) {
        final int maxCharacters = 100_000;
        if (documentText.length() > maxCharacters) {
            documentText = documentText.substring(0, maxCharacters);
        }
        log.info("Sending {} characters from {} to LLM for structuring", documentText.length(), logName);

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "Document Text:\n\n" + documentText)
            ),
            "response_format", Map.of("type", "json_object"),
            "temperature", 0.1
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Groq response: " + e.getMessage(), e);
        }
        return root.path("choices").get(0).path("message").path("content").asText().trim();
    }

    /**
     * Find the tessdata directory in common installation locations.
     *
     * Search order:
     *   1. TESSDATA_PREFIX env var (highest priority — set in Docker/k8s)
     *   2. Standard Windows install paths (local dev)
     *   3. Linux paths used by Docker image (Ubuntu Jammy: Tesseract 4.x or 5.x)
     *   4. macOS Homebrew
     *
     * Returns the *datapath* (parent of tessdata/), or null to let Tess4J
     * use its own default search which reads TESSDATA_PREFIX from the env.
     */
    private String findTessdata() {
        String envPrefix = System.getenv("TESSDATA_PREFIX");
        String[] candidates = {
            // 1. Environment override — set in Docker or CI
            envPrefix != null ? envPrefix : "",
            // 2. Windows (WinGet / UB-Mannheim installer)
            "C:\\Program Files\\Tesseract-OCR\\tessdata",
            "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata",
            // 3. Linux Docker (Ubuntu Jammy apt package — Tesseract 4.x)
            "/usr/share/tesseract-ocr/4.00/tessdata",
            // 3b. Tesseract 5.x on newer Ubuntu/Debian
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tesseract-ocr/5.00/tessdata",
            // 3c. Common Linux fallback
            "/usr/share/tessdata",
            "/usr/local/share/tessdata",
            // 4. macOS Homebrew
            "/opt/homebrew/share/tessdata",
            "/usr/local/Cellar/tesseract/share/tessdata"
        };
        for (String path : candidates) {
            if (path != null && !path.isBlank() && new java.io.File(path).isDirectory()) {
                log.info("Tesseract tessdata found at: {}", path);
                // datapath must point to the *parent* of tessdata/
                return new java.io.File(path).getParent();
            }
        }
        log.warn("Tesseract tessdata directory not found — OCR may fail. " +
                 "Set TESSDATA_PREFIX env var or install Tesseract.");
        return null;
    }

    /**
     * Extracts text from PDF files using PDFBox, or reads plain text.
     */
    public String extractRawText(Path filePath, String fileName) {
        String lower = fileName.toLowerCase();
        try {
            if (lower.endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document).trim();
                }
            }
            throw new IOException("Only PDF extraction is configured");
        } catch (Exception e) {
            log.warn("Could not extract raw text from {}: {}", fileName, e.getMessage());
            return "";
        }
    }
}

