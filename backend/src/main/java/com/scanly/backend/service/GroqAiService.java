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

        log.debug("Groq structured output: {}", structuredJson);
        return structuredJson.trim();
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
            } else {
                return Files.readString(filePath).trim();
            }
        } catch (Exception e) {
            log.warn("Could not extract raw text from {}: {}", fileName, e.getMessage());
            return "";
        }
    }
}
