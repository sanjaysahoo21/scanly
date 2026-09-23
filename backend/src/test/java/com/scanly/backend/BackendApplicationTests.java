package com.scanly.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void objectMapperSerializesInvoiceDateFieldsForJsonExports() throws Exception {
		String json = objectMapper.writeValueAsString(Map.of(
			"invoiceDate", LocalDate.of(2026, 9, 23),
			"exportedAt", Instant.parse("2026-09-23T12:00:00Z")
		));

		assertTrue(json.contains("2026-09-23"));
		assertTrue(json.contains("2026-09-23T12:00:00Z"));
	}

}
