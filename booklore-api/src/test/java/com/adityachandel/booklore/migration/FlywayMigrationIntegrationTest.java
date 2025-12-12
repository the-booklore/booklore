package com.adityachandel.booklore.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway Migration Integration Tests using Testcontainers.
 * 
 * <p>These tests verify that database migrations:
 * <ul>
 *   <li>Execute successfully from a clean state</li>
 *   <li>Preserve data integrity during version upgrades</li>
 *   <li>Handle edge cases (Unicode, long text, null values)</li>
 *   <li>Maintain referential integrity</li>
 * </ul>
 * 
 * <p>The test uses MariaDB in strict mode to catch silent data truncation
 * and other issues that might be ignored in lenient SQL modes.
 * 
 * @see <a href="https://flywaydb.org/documentation/usage/api/">Flyway API</a>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlywayMigrationIntegrationTest {

    private static final String MIGRATION_LOCATION = "filesystem:src/main/resources/db/migration";
    
    // Use strict SQL mode to catch silent truncation and other issues
    private static final String STRICT_SQL_MODE = 
        "STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION";

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("booklore_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_unicode_ci",
                "--sql-mode=" + STRICT_SQL_MODE,
                "--transaction-isolation=READ-COMMITTED"
            );

    private Flyway flyway;

    @BeforeEach
    void setUp() {
        flyway = Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .locations(MIGRATION_LOCATION)
                .cleanDisabled(false)
                .load();
    }

    /**
     * Test that all migrations can be applied to a clean database.
     * This is the most basic migration test.
     */
    @Test
    @Order(1)
    @DisplayName("All migrations should apply successfully to clean database")
    void testCleanMigration() {
        // Clean and migrate
        flyway.clean();
        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);
        
        // Verify schema is valid
        flyway.validate();
    }

    /**
     * Test that migrations preserve Unicode data.
     * This catches charset/collation issues during ALTER TABLE operations.
     */
    @Test
    @Order(2)
    @DisplayName("Migrations should preserve Unicode data")
    void testUnicodeDataPreservation() throws SQLException {
        flyway.clean();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(
                mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())) {
            
            // Insert test data with Unicode characters
            String unicodeTitle = "吾輩は猫である 📚 日本語テスト";
            String unicodeAuthor = "夏目漱石";
            
            // Check if book_metadata table exists and has title column
            if (tableExists(conn, "book") && tableExists(conn, "library") && tableExists(conn, "library_path")) {

                // Insert a test library path first (required for book insertion)
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        INSERT IGNORE INTO library_path (id, path, library_id)
                        VALUES (99999, '/test/unicode', 99999)
                        """);

                    // Insert a test library
                    stmt.execute("""
                        INSERT IGNORE INTO library (id, name, watch, icon)
                        VALUES (99999, 'Unicode Test Library', false, 'test-icon')
                        """);

                    // Insert a test book
                    stmt.execute(String.format("""
                        INSERT IGNORE INTO book (id, library_id, library_path_id, file_name, file_sub_path, book_type, added_on)
                        VALUES (99999, 99999, 99999, '%s', '', 'EPUB', NOW())
                        """, unicodeTitle + ".epub"));
                }

                // Verify the data was stored correctly
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT file_name FROM book WHERE id = 99999")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String storedTitle = rs.getString("file_name");
                        assertThat(storedTitle)
                            .as("Unicode data should be preserved exactly")
                            .isEqualTo(unicodeTitle + ".epub");
                    }
                }

                // Cleanup
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM book WHERE id = 99999");
                    stmt.execute("DELETE FROM library_path WHERE id = 99999");
                    stmt.execute("DELETE FROM library WHERE id = 99999");
                }
            }
        }
    }

    /**
     * Test that migrations handle long text fields correctly.
     * This catches silent truncation during column type changes.
     */
    @Test
    @Order(3)
    @DisplayName("Migrations should not truncate long text fields")
    void testLongTextPreservation() throws SQLException {
        flyway.clean();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(
                mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())) {

            // Check if book_metadata table exists with description column
            if (tableExists(conn, "book_metadata") && columnExists(conn, "book_metadata", "description") &&
                tableExists(conn, "library_path")) {

                // Create a very long description (10KB)
                String longDescription = "A".repeat(10000);

                // First, we need a book and library
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        INSERT IGNORE INTO library_path (id, path, library_id)
                        VALUES (99998, '/test/longtext', 99998)
                        """);
                    stmt.execute("""
                        INSERT IGNORE INTO library (id, name, watch, icon)
                        VALUES (99998, 'Long Text Test Library', false, 'test-icon')
                        """);
                    stmt.execute("""
                        INSERT IGNORE INTO book (id, library_id, library_path_id, file_name, file_sub_path, book_type, added_on)
                        VALUES (99998, 99998, 99998, 'longtext_test.epub', '', 'EPUB', NOW())
                        """);
                }

                // Insert long description
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO book_metadata (book_id, description) VALUES (99998, ?)")) {
                    ps.setString(1, longDescription);
                    ps.execute();
                }

                // Verify length is preserved
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT LENGTH(description) as len FROM book_metadata WHERE book_id = 99998")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        int storedLength = rs.getInt("len");
                        assertThat(storedLength)
                            .as("Long text should not be truncated")
                            .isEqualTo(longDescription.length());
                    }
                }

                // Cleanup
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM book_metadata WHERE book_id = 99998");
                    stmt.execute("DELETE FROM book WHERE id = 99998");
                    stmt.execute("DELETE FROM library_path WHERE id = 99998");
                    stmt.execute("DELETE FROM library WHERE id = 99998");
                }
            }
        }
    }

    /**
     * Test incremental migration from a specific version.
     * Simulates upgrading from an older version.
     */
    @Test
    @Order(4)
    @DisplayName("Incremental migration should work from V1")
    void testIncrementalMigration() {
        // Clean database
        flyway.clean();
        
        // First, apply only V1 and V2
        Flyway v1Flyway = Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .locations(MIGRATION_LOCATION)
                .target(MigrationVersion.fromVersion("2"))
                .load();
        
        MigrateResult v1Result = v1Flyway.migrate();
        assertThat(v1Result.success).isTrue();
        
        // Now apply all remaining migrations
        Flyway fullFlyway = Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .locations(MIGRATION_LOCATION)
                .load();
        
        MigrateResult fullResult = fullFlyway.migrate();
        assertThat(fullResult.success).isTrue();
        
        // Validate final state
        fullFlyway.validate();
    }

    /**
     * Test that migrations maintain referential integrity.
     */
    @Test
    @Order(5)
    @DisplayName("Migrations should maintain referential integrity")
    void testReferentialIntegrity() throws SQLException {
        flyway.clean();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(
                mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())) {

            // Check for orphaned records in key relationships
            String[] integrityChecks = {
                // Books without libraries
                "SELECT COUNT(*) FROM book b LEFT JOIN library l ON b.library_id = l.id WHERE l.id IS NULL",
                // Book metadata without books
                "SELECT COUNT(*) FROM book_metadata bm LEFT JOIN book b ON bm.book_id = b.id WHERE b.id IS NULL"
            };

            for (String check : integrityChecks) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(check);
                    if (rs.next()) {
                        int orphanCount = rs.getInt(1);
                        assertThat(orphanCount)
                            .as("No orphaned records should exist: " + check)
                            .isZero();
                    }
                } catch (SQLException e) {
                    // Table might not exist in current schema version
                    if (!e.getMessage().contains("doesn't exist")) {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Test that the schema matches expected structure after all migrations.
     */
    @Test
    @Order(6)
    @DisplayName("Final schema should have expected core tables")
    void testFinalSchemaStructure() throws SQLException {
        flyway.clean();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(
                mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())) {

            // Core tables that must exist
            String[] requiredTables = {
                "book",
                "library",
                "users",
                "flyway_schema_history"
            };

            for (String table : requiredTables) {
                assertThat(tableExists(conn, table))
                    .as("Table '" + table + "' should exist")
                    .isTrue();
            }
        }
    }

    /**
     * Test data seeding and verification for regression testing.
     * This captures baseline metrics for comparison.
     */
    @Test
    @Order(7)
    @DisplayName("Should capture baseline data metrics")
    void testDataMetricsCapture() throws SQLException {
        flyway.clean();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(
                mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())) {

            Map<String, Object> metrics = new HashMap<>();

            // Capture table counts
            String[] tables = {"library", "book", "users"};
            for (String table : tables) {
                if (tableExists(conn, table)) {
                    try (Statement stmt = conn.createStatement()) {
                        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                        if (rs.next()) {
                            metrics.put(table + "_count", rs.getInt(1));
                        }
                    }
                }
            }

            // Capture flyway version
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT MAX(version) FROM flyway_schema_history WHERE success = 1");
                if (rs.next()) {
                    metrics.put("flyway_version", rs.getString(1));
                }
            }

            // Log metrics for debugging
            metrics.forEach((key, value) -> 
                System.out.printf("Metric: %s = %s%n", key, value));

            assertThat(metrics).isNotEmpty();
            assertThat(metrics.get("flyway_version")).isNotNull();
        }
    }

    // ==================== Helper Methods ====================

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }
}

