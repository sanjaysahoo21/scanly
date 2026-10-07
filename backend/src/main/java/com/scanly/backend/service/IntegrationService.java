package com.scanly.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanly.backend.dto.InvoiceResponse;
import com.scanly.backend.entity.IntegrationConfig;
import com.scanly.backend.entity.IntegrationPushLog;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.repository.IntegrationConfigRepository;
import com.scanly.backend.repository.IntegrationPushLogRepository;
import com.scanly.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;

/**
 * IntegrationService — Part 1 + Part 2.
 *
 * Part 1: CRUD + testConnection() with user-friendly error messages.
 * Part 2: pushInvoices() — Tier 1 auto-create DDL + INSERT per invoice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {

    private final IntegrationConfigRepository integrationRepo;
    private final IntegrationPushLogRepository pushLogRepo;
    private final InvoiceRepository invoiceRepository;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    // ── Connection test timeout ───────────────────────────────────────────────
    private static final int CONNECT_TIMEOUT_SECONDS = 6;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<IntegrationConfig> listForOrg(Organization org) {
        return integrationRepo.findByOrganizationOrderByCreatedAtDesc(org);
    }

    public Optional<IntegrationConfig> findByIdAndOrg(UUID id, Organization org) {
        return integrationRepo.findByIdAndOrganization(id, org);
    }

    @Transactional
    public IntegrationConfig save(IntegrationConfig config) {
        return integrationRepo.save(config);
    }

    @Transactional
    public boolean delete(UUID id, Organization org) {
        return integrationRepo.findByIdAndOrganization(id, org).map(cfg -> {
            integrationRepo.delete(cfg);
            log.info("Integration {} deleted for org {}", id, org.getId());
            return true;
        }).orElse(false);
    }

    // ── Push Logs ──────────────────────────────────────────────────────────────

    public List<IntegrationPushLog> getLogsForIntegration(IntegrationConfig cfg) {
        return pushLogRepo.findByIntegrationOrderByPushedAtDesc(cfg);
    }

    // ── Connection Test ───────────────────────────────────────────────────────

    /**
     * Tests the connection using the provided raw (unencrypted) password.
     * Used during the setup wizard before saving — password not yet in DB.
     */
    public ConnectionTestResult testConnection(String dbType, String host, int port,
                                               String databaseName, String username,
                                               String rawPassword, boolean useSsl) {
        String url = buildJdbcUrl(dbType, host, port, databaseName, useSsl);
        if (url == null) {
            return ConnectionTestResult.failure("Unsupported database type: " + dbType +
                ". Supported: postgresql, mysql");
        }

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", rawPassword);
        props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        props.setProperty("loginTimeout",   String.valueOf(CONNECT_TIMEOUT_SECONDS));
        props.setProperty("socketTimeout",  String.valueOf(CONNECT_TIMEOUT_SECONDS * 1000));

        try {
            loadDriver(dbType);
        } catch (ClassNotFoundException e) {
            return ConnectionTestResult.failure(
                "Driver for " + dbType + " is not available. " +
                "Add the JDBC dependency to pom.xml.");
        }

        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.isValid(CONNECT_TIMEOUT_SECONDS);
            long ms = System.currentTimeMillis() - start;
            log.info("Connection test OK for {}:{}/{} in {}ms", host, port, databaseName, ms);
            return ConnectionTestResult.success(ms);
        } catch (SQLException ex) {
            String msg = classifySqlException(ex, host, port, dbType);
            log.warn("Connection test failed for {}:{}/{}: {} — {}",
                host, port, databaseName, ex.getSQLState(), ex.getMessage());
            return ConnectionTestResult.failure(msg);
        }
    }

    /**
     * Tests the connection for an already-saved integration (decrypts password first).
     */
    public ConnectionTestResult testSavedConnection(IntegrationConfig cfg) {
        String rawPassword = cryptoService.decrypt(cfg.getEncryptedPassword());
        return testConnection(cfg.getDbType(), cfg.getHost(), cfg.getPort(),
            cfg.getDatabaseName(), cfg.getUsername(), rawPassword, cfg.getUseSsl());
    }

    // ── Push Invoices (Tier 1 — Auto-create) ─────────────────────────────────

    /**
     * Pushes a list of invoices into the target database.
     *
     * Tier 1 (targetTable == null):
     *   - Creates scanly_invoices + scanly_line_items tables if not present (idempotent DDL).
     *   - Inserts each invoice. On conflict (duplicate id), skips gracefully.
     *
     * @param cfg        the saved integration config
     * @param invoiceIds list of invoice UUIDs to push (null or empty = push all for org)
     * @param pushAll    if true, ignore invoiceIds and push every invoice for the org
     */
    public PushResult pushInvoices(IntegrationConfig cfg,
                                   List<UUID> invoiceIds,
                                   boolean pushAll) {
        String rawPassword = cryptoService.decrypt(cfg.getEncryptedPassword());
        String url = buildJdbcUrl(cfg.getDbType(), cfg.getHost(), cfg.getPort(),
            cfg.getDatabaseName(), cfg.getUseSsl());

        // Load invoices to push
        List<InvoiceResponse> invoices;
        try {
            var entities = pushAll
                ? invoiceRepository.findAllByDocumentOrganizationId(cfg.getOrganization().getId())
                : invoiceRepository.findByIdInAndDocumentOrganizationId(
                      invoiceIds, cfg.getOrganization().getId());
            invoices = entities.stream().map(InvoiceResponse::from).toList();
        } catch (Exception e) {
            log.error("Failed to load invoices for push: {}", e.getMessage());
            return savePushLog(cfg, 0, 0, 0,
                List.of(), "FAILED");
        }

        if (invoices.isEmpty()) {
            return savePushLog(cfg, 0, 0, 0, List.of(), "SUCCESS");
        }

        Properties props = new Properties();
        props.setProperty("user", cfg.getUsername());
        props.setProperty("password", rawPassword);
        props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));

        List<PushFailure> failures = new ArrayList<>();
        int succeeded = 0;

        try {
            loadDriver(cfg.getDbType());
        } catch (ClassNotFoundException e) {
            return savePushLog(cfg, invoices.size(), 0, invoices.size(),
                List.of(new PushFailure(null, null, "JDBC driver not available: " + e.getMessage())),
                "FAILED");
        }

        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setAutoCommit(true);

            // ── Tier 1: ensure tables exist ───────────────────────────────────
            if (cfg.getTargetTable() == null || cfg.getTargetTable().isBlank()) {
                runDDL(conn, cfg.getDbType());
            }

            // ── Insert each invoice ───────────────────────────────────────────
            for (InvoiceResponse inv : invoices) {
                try {
                    insertInvoiceHeader(conn, inv, cfg.getDbType());

                    // Insert line items if strategy is not "skip"
                    if (!"skip".equals(cfg.getLineItemsStrategy()) &&
                        inv.getLineItems() != null && !inv.getLineItems().isEmpty()) {
                        insertLineItems(conn, inv, cfg.getDbType());
                    }
                    succeeded++;
                } catch (SQLException ex) {
                    String errMsg = classifyInsertError(ex, inv);
                    log.warn("Push failed for invoice {}: {}", inv.getId(), errMsg);
                    failures.add(new PushFailure(inv.getId(), inv.getInvoiceNumber(), errMsg));
                }
            }
        } catch (SQLException connEx) {
            String connMsg = classifySqlException(connEx, cfg.getHost(), cfg.getPort(), cfg.getDbType());
            log.error("Push connection failed for integration {}: {}", cfg.getId(), connMsg);
            return savePushLog(cfg, invoices.size(), 0, invoices.size(),
                List.of(new PushFailure(null, null, connMsg)), "FAILED");
        }

        String status = failures.isEmpty() ? "SUCCESS"
            : (succeeded == 0 ? "FAILED" : "PARTIAL");

        log.info("Push complete for integration {}: {}/{} succeeded, {} failed, status={}",
            cfg.getId(), succeeded, invoices.size(), failures.size(), status);

        return savePushLog(cfg, invoices.size(), succeeded, failures.size(), failures, status);
    }

    // ── DDL runner ────────────────────────────────────────────────────────────

    private void runDDL(Connection conn, String dbType) throws SQLException {
        String ddl = DDLGenerator.generate(dbType);

        // Strip comment lines first so they don't contaminate statement splitting
        String cleaned = java.util.Arrays.stream(ddl.split("\n"))
            .filter(line -> !line.strip().startsWith("--"))
            .collect(java.util.stream.Collectors.joining("\n"));

        for (String stmt : cleaned.split(";")) {
            String trimmed = stmt.strip();
            if (!trimmed.isEmpty()) {
                try (Statement s = conn.createStatement()) {
                    s.execute(trimmed);
                    log.debug("DDL OK: {}", trimmed.substring(0, Math.min(60, trimmed.length())));
                } catch (SQLException ex) {
                    // IF NOT EXISTS should prevent most duplicates — but log and rethrow
                    log.error("DDL failed [{}]: {}", trimmed.substring(0, Math.min(60, trimmed.length())), ex.getMessage());
                    throw ex;
                }
            }
        }
        log.info("DDL executed successfully for dbType={}", dbType);
    }

    // ── INSERT helpers (Tier 1) ───────────────────────────────────────────────

    private void insertInvoiceHeader(Connection conn, InvoiceResponse inv,
                                     String dbType) throws SQLException {
        // ON CONFLICT DO NOTHING — safe to push same invoice twice
        String sql = switch (dbType.toLowerCase()) {
            case "postgresql" -> """
                INSERT INTO scanly_invoices
                  (id, invoice_number, vendor_name, vendor_address, vendor_gstin,
                   buyer_name, buyer_address, buyer_gstin,
                   invoice_date, due_date, subtotal, tax_amount, discount_amount,
                   total_amount, currency, is_audited, has_errors, is_duplicate,
                   validation_issues, pushed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())
                ON CONFLICT (id) DO NOTHING
                """;
            case "mysql" -> """
                INSERT IGNORE INTO scanly_invoices
                  (id, invoice_number, vendor_name, vendor_address, vendor_gstin,
                   buyer_name, buyer_address, buyer_gstin,
                   invoice_date, due_date, subtotal, tax_amount, discount_amount,
                   total_amount, currency, is_audited, has_errors, is_duplicate,
                   validation_issues, pushed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())
                """;
            default -> throw new SQLException("Unsupported dbType: " + dbType);
        };

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1,  inv.getId());                // UUID
            ps.setString(2,  inv.getInvoiceNumber());
            ps.setString(3,  inv.getVendorName());
            ps.setString(4,  inv.getVendorAddress());
            ps.setString(5,  inv.getVendorGstin());
            ps.setString(6,  inv.getBuyerName());
            ps.setString(7,  inv.getBuyerAddress());
            ps.setString(8,  inv.getBuyerGstin());
            ps.setObject(9,  inv.getInvoiceDate());       // LocalDate
            ps.setObject(10, inv.getDueDate());           // LocalDate
            ps.setBigDecimal(11, inv.getSubtotal());
            ps.setBigDecimal(12, inv.getTaxAmount());
            ps.setBigDecimal(13, inv.getDiscountAmount());
            ps.setBigDecimal(14, inv.getTotalAmount());
            ps.setString(15, inv.getCurrency() != null ? inv.getCurrency().name() : null);
            ps.setBoolean(16, Boolean.TRUE.equals(inv.getIsAudited()));
            ps.setBoolean(17, Boolean.TRUE.equals(inv.getHasValidationErrors()));
            ps.setBoolean(18, Boolean.TRUE.equals(inv.getIsDuplicate()));
            ps.setString(19, inv.getValidationIssues());
            ps.executeUpdate();
        }
    }

    private void insertLineItems(Connection conn, InvoiceResponse inv,
                                 String dbType) throws SQLException {
        String sql = switch (dbType.toLowerCase()) {
            case "postgresql" -> """
                INSERT INTO scanly_line_items
                  (id, invoice_id, description, hsn_code, quantity,
                   unit_price, tax_rate, total_price)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                """;
            case "mysql" -> """
                INSERT IGNORE INTO scanly_line_items
                  (id, invoice_id, description, hsn_code, quantity,
                   unit_price, tax_rate, total_price)
                VALUES (?,?,?,?,?,?,?,?)
                """;
            default -> throw new SQLException("Unsupported dbType: " + dbType);
        };

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (InvoiceResponse.LineItemDto li : inv.getLineItems()) {
                UUID liId = li.getId() != null ? li.getId() : UUID.randomUUID();
                ps.setObject(1, liId);                    // UUID
                ps.setObject(2, inv.getId());             // UUID
                ps.setString(3, li.getDescription());
                ps.setString(4, li.getHsnCode());
                ps.setBigDecimal(5, li.getQuantity());
                ps.setBigDecimal(6, li.getUnitPrice());
                ps.setBigDecimal(7, li.getTaxRate());
                ps.setBigDecimal(8, li.getTotalPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Push log persistence ──────────────────────────────────────────────────

    private PushResult savePushLog(IntegrationConfig cfg, int total, int succeeded,
                                   int failed, List<PushFailure> failures, String status) {
        try {
            String failuresJson = objectMapper.writeValueAsString(failures);
            IntegrationPushLog log = IntegrationPushLog.builder()
                .integration(cfg)
                .totalAttempted(total)
                .totalSucceeded(succeeded)
                .totalFailed(failed)
                .failedInvoices(failuresJson)
                .status(status)
                .build();
            pushLogRepo.save(log);
        } catch (Exception e) {
            log.warn("Failed to save push log: {}", e.getMessage());
        }
        return new PushResult(total, succeeded, failed, failures, status);
    }

    // ── Insert error classifier ───────────────────────────────────────────────

    private String classifyInsertError(SQLException ex, InvoiceResponse inv) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.toLowerCase().contains("value too long") || msg.toLowerCase().contains("data too long")) {
            return "Value too long for a column. Invoice: " + inv.getInvoiceNumber();
        }
        if (msg.toLowerCase().contains("not-null") || msg.toLowerCase().contains("null value")) {
            return "Null value violates NOT NULL constraint. Invoice: " + inv.getInvoiceNumber();
        }
        if (msg.toLowerCase().contains("duplicate key") || msg.toLowerCase().contains("unique constraint")) {
            return "Duplicate key — invoice already exists in target table.";
        }
        return msg;
    }

    // ── JDBC URL Builder ──────────────────────────────────────────────────────

    static String buildJdbcUrl(String dbType, String host, int port,
                                String database, boolean ssl) {
        return switch (dbType.toLowerCase()) {
            case "postgresql" -> {
                String base = "jdbc:postgresql://" + host + ":" + port + "/" + database;
                yield ssl ? base + "?sslmode=require" : base + "?sslmode=disable";
            }
            case "mysql" -> {
                String base = "jdbc:mysql://" + host + ":" + port + "/" + database;
                yield base + "?useSSL=" + ssl +
                      "&allowPublicKeyRetrieval=true" +
                      "&serverTimezone=UTC";
            }
            default -> null;
        };
    }

    private void loadDriver(String dbType) throws ClassNotFoundException {
        switch (dbType.toLowerCase()) {
            case "postgresql" -> Class.forName("org.postgresql.Driver");
            case "mysql"      -> Class.forName("com.mysql.cj.jdbc.Driver");
        }
    }

    // ── Error Classifier ──────────────────────────────────────────────────────

    private String classifySqlException(SQLException ex, String host, int port, String dbType) {
        String msg  = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String state = ex.getSQLState() != null ? ex.getSQLState() : "";

        if (msg.contains("connection refused") || msg.contains("connect timed out") ||
            msg.contains("communications link failure") || msg.contains("network unreachable")) {
            return "Cannot reach the database server at " + host + ":" + port + ". " +
                   "Check that the server is running and the port is accessible.";
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "Connection timed out after " + CONNECT_TIMEOUT_SECONDS + " seconds.";
        }
        if (msg.contains("password authentication failed") || msg.contains("access denied for user") ||
            state.equals("28P01") || state.equals("28000")) {
            return "Authentication failed. Check your username and password.";
        }
        if (msg.contains("ssl") || msg.contains("tls")) {
            if (msg.contains("required") || msg.contains("ssl connection")) {
                return "This database requires SSL. Enable the SSL toggle and try again.";
            }
            return "SSL/TLS error: " + ex.getMessage() + ". Try toggling the SSL option.";
        }
        if (msg.contains("database") && (msg.contains("does not exist") || msg.contains("unknown database"))) {
            return "Database not found. Check the database name field.";
        }
        if (msg.contains("unknown host") || msg.contains("nodename nor servname")) {
            return "Hostname '" + host + "' could not be resolved. Check the host field.";
        }
        return "Connection failed: " + ex.getMessage();
    }

    // ── Crypto helper for controller ──────────────────────────────────────────

    public String encryptPassword(String rawPassword) {
        return cryptoService.encrypt(rawPassword);
    }

    // ── Result / inner types ──────────────────────────────────────────────────

    public record ConnectionTestResult(boolean success, String message, Long latencyMs) {
        static ConnectionTestResult success(long ms) {
            return new ConnectionTestResult(true, "Connection successful", ms);
        }
        static ConnectionTestResult failure(String message) {
            return new ConnectionTestResult(false, message, null);
        }
    }

    public record PushFailure(UUID invoiceId, String invoiceNumber, String error) {}

    public record PushResult(int totalAttempted, int totalSucceeded, int totalFailed,
                             List<PushFailure> failures, String status) {}
}
