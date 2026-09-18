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

    /** Vision model used for image/receipt processing. */
    @Value("${groq.vision.model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String visionModel;

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
     * Process a scanned image or photo of an invoice using a vision-capable LLM.
     *
     * The image is base64-encoded and sent as an OpenAI-compatible vision message.
     * Returns structured invoice JSON in the same format as extractAndStructureInvoice().
     *
     * @param filePath  path to the image file on disk (JPG / PNG / WEBP)
     * @param fileName  original filename (used for logging and MIME detection)
     */
    public String extractAndStructureImage(Path filePath, String fileName) throws IOException {
        byte[] imageBytes = Files.readAllBytes(filePath);
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = resolveMimeType(fileName);

        log.info("Sending image {} ({} bytes) to vision model: {}", fileName, imageBytes.length, visionModel);

        // Vision message: content is a list with text + image_url parts
        Map<String, Object> textPart  = Map.of("type", "text",      "text", SYSTEM_PROMPT);
        Map<String, Object> imagePart = Map.of("type", "image_url",
            "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64Image));

        Map<String, Object> userMessage = Map.of(
            "role", "user",
            "content", List.of(textPart, imagePart)
        );

        Map<String, Object> requestBody = Map.of(
            "model", visionModel,
            "messages", List.of(userMessage),
            "temperature", 0.1,
            "max_tokens", 2048
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq Vision API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String raw = root.path("choices").get(0)
            .path("message").path("content").asText();

        // Strip markdown code fences the model sometimes adds
        return raw.replaceAll("(?s)^```json\\s*", "")
                  .replaceAll("(?s)```\\s*$", "")
                  .trim();
    }

    /** Resolve MIME type from file extension for base64 data URI. */
    private String resolveMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png"))               return "image/png";
        if (lower.endsWith(".webp"))              return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "image/jpeg"; // safe default
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
