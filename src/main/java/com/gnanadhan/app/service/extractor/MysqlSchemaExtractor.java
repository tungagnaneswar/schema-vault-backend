package com.gnanadhan.app.service.extractor;

import com.gnanadhan.app.dto.schema.*;
import com.gnanadhan.app.entity.DatabaseEngine;
import com.gnanadhan.app.entity.DbConnection;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * MySQL-specific schema extractor.
 * <p>
 * Implements the {@link SchemaExtractor} Strategy interface to extract
 * schema metadata from MySQL databases. Uses MySQL's information_schema
 * views and mysql system tables for metadata introspection.
 * </p>
 * <p>
 * Because it produces the same {@link SchemaModel} as every other extractor,
 * the comparison engine ({@code SchemaComparisonService}) works with MySQL
 * schemas without any modification — the Strategy pattern in action.
 * </p>
 */
@Service
public class MysqlSchemaExtractor implements SchemaExtractor {

    @Override
    public boolean supports(DatabaseEngine engine) {
        return engine == DatabaseEngine.MYSQL;
    }

    @Override
    public SchemaModel extract(DbConnection connection, String decryptedPassword) {
        String url = connection.getEngine().buildJdbcUrl(
            connection.getHost(), connection.getPort(), connection.getDatabaseName());

        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), decryptedPassword)) {

            String dbName = connection.getDatabaseName();
            String tableFilter = buildTableFilter(connection.getExcludedTables());

            List<TableModel> tables = extractTables(conn, dbName, tableFilter);
            List<FunctionModel> functions = extractFunctions(conn, dbName);
            List<ProcedureModel> procedures = extractProcedures(conn, dbName);
            List<SequenceModel> sequences = Collections.emptyList(); // MySQL has AUTO_INCREMENT, no standalone sequences
            List<TypeModel> types = Collections.emptyList(); // MySQL does not support custom types
            List<ViewModel> views = extractViews(conn, dbName, tableFilter);

            return SchemaModel.builder()
                    .databaseName(dbName)
                    .tables(tables)
                    .functions(functions)
                    .procedures(procedures)
                    .sequences(sequences)
                    .types(types)
                    .views(views)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Error extracting schema for " + connection.getDatabaseName(), e);
        }
    }

    private String buildTableFilter(String excludedTables) {
        if (excludedTables == null || excludedTables.trim().isEmpty()) {
            return "";
        }
        String[] tables = excludedTables.split(",");
        StringBuilder sb = new StringBuilder(" AND TABLE_NAME NOT IN (");
        for (int i = 0; i < tables.length; i++) {
            sb.append("'").append(tables[i].trim()).append("'");
            if (i < tables.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    // ─── Tables ───────────────────────────────────────────────────────────────

    private List<TableModel> extractTables(Connection conn, String dbName, String tableFilter) throws SQLException {
        List<TableModel> tables = new ArrayList<>();

        Map<String, List<ColumnModel>> allColumns = extractAllColumns(conn, dbName);
        Map<String, List<String>> allPrimaryKeys = extractAllPrimaryKeys(conn, dbName);
        Map<String, List<ConstraintModel>> allConstraints = extractAllConstraints(conn, dbName);
        Map<String, List<ForeignKeyModel>> allForeignKeys = extractAllForeignKeys(conn, dbName);
        Map<String, List<IndexModel>> allIndexes = extractAllIndexes(conn, dbName);
        Map<String, List<TriggerModel>> allTriggers = extractAllTriggers(conn, dbName);

        String sql = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                     "WHERE TABLE_SCHEMA = '" + dbName + "' AND TABLE_TYPE = 'BASE TABLE'" +
                     tableFilter +
                     " ORDER BY TABLE_NAME";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                tables.add(TableModel.builder()
                        .name(tableName)
                        .columns(allColumns.getOrDefault(tableName, Collections.emptyList()))
                        .primaryKeys(allPrimaryKeys.getOrDefault(tableName, Collections.emptyList()))
                        .constraints(allConstraints.getOrDefault(tableName, Collections.emptyList()))
                        .foreignKeys(allForeignKeys.getOrDefault(tableName, Collections.emptyList()))
                        .indexes(allIndexes.getOrDefault(tableName, Collections.emptyList()))
                        .triggers(allTriggers.getOrDefault(tableName, Collections.emptyList()))
                        .build());
            }
        }

        return tables;
    }

    // ─── Columns ──────────────────────────────────────────────────────────────

    private Map<String, List<ColumnModel>> extractAllColumns(Connection conn, String dbName) throws SQLException {
        Map<String, List<ColumnModel>> map = new HashMap<>();

        String sql = "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, " +
                     "ORDINAL_POSITION, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE " +
                     "FROM information_schema.COLUMNS " +
                     "WHERE TABLE_SCHEMA = '" + dbName + "' " +
                     "ORDER BY TABLE_NAME, ORDINAL_POSITION";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                map.computeIfAbsent(tableName, k -> new ArrayList<>()).add(ColumnModel.builder()
                        .name(rs.getString("COLUMN_NAME"))
                        .type(rs.getString("DATA_TYPE"))
                        .isNullable("YES".equals(rs.getString("IS_NULLABLE")))
                        .defaultValue(rs.getString("COLUMN_DEFAULT"))
                        .ordinalPosition(rs.getInt("ORDINAL_POSITION"))
                        .maxLength(getIntOrNull(rs, "CHARACTER_MAXIMUM_LENGTH"))
                        .numericPrecision(getIntOrNull(rs, "NUMERIC_PRECISION"))
                        .numericScale(getIntOrNull(rs, "NUMERIC_SCALE"))
                        .build());
            }
        }

        return map;
    }

    // ─── Primary Keys ─────────────────────────────────────────────────────────

    private Map<String, List<String>> extractAllPrimaryKeys(Connection conn, String dbName) throws SQLException {
        Map<String, List<String>> map = new HashMap<>();

        String sql = "SELECT TABLE_NAME, COLUMN_NAME " +
                     "FROM information_schema.KEY_COLUMN_USAGE " +
                     "WHERE TABLE_SCHEMA = '" + dbName + "' " +
                     "  AND CONSTRAINT_NAME = 'PRIMARY' " +
                     "ORDER BY TABLE_NAME, ORDINAL_POSITION";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                map.computeIfAbsent(tableName, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
            }
        }

        return map;
    }

    // ─── Constraints (CHECK, UNIQUE) ──────────────────────────────────────────

    private Map<String, List<ConstraintModel>> extractAllConstraints(Connection conn, String dbName) throws SQLException {
        Map<String, List<ConstraintModel>> map = new HashMap<>();

        // UNIQUE constraints
        String uniqueSql = "SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, " +
                           "GROUP_CONCAT(kcu.COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION) AS columns " +
                           "FROM information_schema.TABLE_CONSTRAINTS tc " +
                           "JOIN information_schema.KEY_COLUMN_USAGE kcu " +
                           "  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                           "  AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA " +
                           "  AND tc.TABLE_NAME = kcu.TABLE_NAME " +
                           "WHERE tc.TABLE_SCHEMA = '" + dbName + "' " +
                           "  AND tc.CONSTRAINT_TYPE = 'UNIQUE' " +
                           "GROUP BY tc.TABLE_NAME, tc.CONSTRAINT_NAME " +
                           "ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(uniqueSql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new ArrayList<>()).add(ConstraintModel.builder()
                        .name(rs.getString("CONSTRAINT_NAME"))
                        .type("UNIQUE")
                        .definition("UNIQUE (" + rs.getString("columns") + ")")
                        .build());
            }
        }

        // CHECK constraints (MySQL 8.0.16+)
        String checkSql = "SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE " +
                          "FROM information_schema.TABLE_CONSTRAINTS tc " +
                          "JOIN information_schema.CHECK_CONSTRAINTS cc " +
                          "  ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME " +
                          "  AND tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA " +
                          "WHERE tc.TABLE_SCHEMA = '" + dbName + "' " +
                          "  AND tc.CONSTRAINT_TYPE = 'CHECK' " +
                          "ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkSql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new ArrayList<>()).add(ConstraintModel.builder()
                        .name(rs.getString("CONSTRAINT_NAME"))
                        .type("CHECK")
                        .definition(rs.getString("CHECK_CLAUSE"))
                        .build());
            }
        }

        return map;
    }

    // ─── Foreign Keys ─────────────────────────────────────────────────────────

    private Map<String, List<ForeignKeyModel>> extractAllForeignKeys(Connection conn, String dbName) throws SQLException {
        Map<String, List<ForeignKeyModel>> map = new HashMap<>();

        String sql = "SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, " +
                     "  GROUP_CONCAT(DISTINCT kcu.COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION) AS columns, " +
                     "  kcu.REFERENCED_TABLE_NAME AS referenced_table, " +
                     "  GROUP_CONCAT(DISTINCT kcu.REFERENCED_COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION) AS referenced_columns, " +
                     "  rc.UPDATE_RULE, rc.DELETE_RULE " +
                     "FROM information_schema.TABLE_CONSTRAINTS tc " +
                     "JOIN information_schema.KEY_COLUMN_USAGE kcu " +
                     "  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                     "  AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA " +
                     "  AND tc.TABLE_NAME = kcu.TABLE_NAME " +
                     "JOIN information_schema.REFERENTIAL_CONSTRAINTS rc " +
                     "  ON tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME " +
                     "  AND tc.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA " +
                     "WHERE tc.TABLE_SCHEMA = '" + dbName + "' " +
                     "  AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY' " +
                     "GROUP BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.REFERENCED_TABLE_NAME, rc.UPDATE_RULE, rc.DELETE_RULE " +
                     "ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new ArrayList<>()).add(ForeignKeyModel.builder()
                        .name(rs.getString("CONSTRAINT_NAME"))
                        .columns(Arrays.asList(rs.getString("columns").split(",\\s*")))
                        .referencedTable(rs.getString("referenced_table"))
                        .referencedColumns(Arrays.asList(rs.getString("referenced_columns").split(",\\s*")))
                        .updateRule(rs.getString("UPDATE_RULE"))
                        .deleteRule(rs.getString("DELETE_RULE"))
                        .build());
            }
        }

        return map;
    }

    // ─── Indexes ──────────────────────────────────────────────────────────────

    private Map<String, List<IndexModel>> extractAllIndexes(Connection conn, String dbName) throws SQLException {
        Map<String, List<IndexModel>> map = new HashMap<>();

        String sql = "SELECT TABLE_NAME, INDEX_NAME, " +
                     "  GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns, " +
                     "  NOT NON_UNIQUE AS is_unique, " +
                     "  INDEX_TYPE " +
                     "FROM information_schema.STATISTICS " +
                     "WHERE TABLE_SCHEMA = '" + dbName + "' " +
                     "  AND INDEX_NAME != 'PRIMARY' " +
                     "GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE, INDEX_TYPE " +
                     "ORDER BY TABLE_NAME, INDEX_NAME";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String columns = rs.getString("columns");
                String indexType = rs.getString("INDEX_TYPE");
                boolean isUnique = rs.getBoolean("is_unique");

                map.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new ArrayList<>()).add(IndexModel.builder()
                        .name(rs.getString("INDEX_NAME"))
                        .columns(Arrays.asList(columns.split(",\\s*")))
                        .isUnique(isUnique)
                        .indexType(indexType)
                        .definition(indexType + " (" + columns + ")")
                        .build());
            }
        }

        return map;
    }

    // ─── Triggers ─────────────────────────────────────────────────────────────

    private Map<String, List<TriggerModel>> extractAllTriggers(Connection conn, String dbName) throws SQLException {
        Map<String, List<TriggerModel>> map = new HashMap<>();

        String sql = "SELECT EVENT_OBJECT_TABLE AS table_name, TRIGGER_NAME, " +
                     "  EVENT_MANIPULATION AS event, " +
                     "  ACTION_TIMING, ACTION_STATEMENT " +
                     "FROM information_schema.TRIGGERS " +
                     "WHERE TRIGGER_SCHEMA = '" + dbName + "' " +
                     "ORDER BY EVENT_OBJECT_TABLE, TRIGGER_NAME";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(TriggerModel.builder()
                        .name(rs.getString("TRIGGER_NAME"))
                        .event(rs.getString("event"))
                        .timing(rs.getString("ACTION_TIMING"))
                        .definition(rs.getString("ACTION_STATEMENT"))
                        .build());
            }
        }

        return map;
    }

    // ─── Functions ────────────────────────────────────────────────────────────

    private List<FunctionModel> extractFunctions(Connection conn, String dbName) throws SQLException {
        List<FunctionModel> functions = new ArrayList<>();

        String sql = "SELECT r.ROUTINE_NAME AS name, " +
                     "  r.DATA_TYPE AS return_type, " +
                     "  GROUP_CONCAT(CONCAT(p.PARAMETER_NAME, ' ', p.DATA_TYPE) " +
                     "    ORDER BY p.ORDINAL_POSITION) AS arguments, " +
                     "  r.EXTERNAL_LANGUAGE AS language, " +
                     "  r.ROUTINE_DEFINITION AS definition " +
                     "FROM information_schema.ROUTINES r " +
                     "LEFT JOIN information_schema.PARAMETERS p " +
                     "  ON r.SPECIFIC_NAME = p.SPECIFIC_NAME " +
                     "  AND r.ROUTINE_SCHEMA = p.SPECIFIC_SCHEMA " +
                     "  AND p.ORDINAL_POSITION > 0 " +
                     "WHERE r.ROUTINE_SCHEMA = '" + dbName + "' " +
                     "  AND r.ROUTINE_TYPE = 'FUNCTION' " +
                     "GROUP BY r.ROUTINE_NAME, r.DATA_TYPE, r.EXTERNAL_LANGUAGE, r.ROUTINE_DEFINITION " +
                     "ORDER BY r.ROUTINE_NAME";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                functions.add(FunctionModel.builder()
                        .name(rs.getString("name"))
                        .returnType(rs.getString("return_type"))
                        .arguments(rs.getString("arguments"))
                        .language(rs.getString("language") != null ? rs.getString("language") : "SQL")
                        .definition(rs.getString("definition"))
                        .build());
            }
        }

        return functions;
    }

    // ─── Procedures ───────────────────────────────────────────────────────────

    private List<ProcedureModel> extractProcedures(Connection conn, String dbName) throws SQLException {
        List<ProcedureModel> procedures = new ArrayList<>();

        String sql = "SELECT r.ROUTINE_NAME AS name, " +
                     "  GROUP_CONCAT(CONCAT(p.PARAMETER_MODE, ' ', p.PARAMETER_NAME, ' ', p.DATA_TYPE) " +
                     "    ORDER BY p.ORDINAL_POSITION) AS arguments, " +
                     "  r.EXTERNAL_LANGUAGE AS language, " +
                     "  r.ROUTINE_DEFINITION AS definition " +
                     "FROM information_schema.ROUTINES r " +
                     "LEFT JOIN information_schema.PARAMETERS p " +
                     "  ON r.SPECIFIC_NAME = p.SPECIFIC_NAME " +
                     "  AND r.ROUTINE_SCHEMA = p.SPECIFIC_SCHEMA " +
                     "  AND p.ORDINAL_POSITION > 0 " +
                     "WHERE r.ROUTINE_SCHEMA = '" + dbName + "' " +
                     "  AND r.ROUTINE_TYPE = 'PROCEDURE' " +
                     "GROUP BY r.ROUTINE_NAME, r.EXTERNAL_LANGUAGE, r.ROUTINE_DEFINITION " +
                     "ORDER BY r.ROUTINE_NAME";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                procedures.add(ProcedureModel.builder()
                        .name(rs.getString("name"))
                        .arguments(rs.getString("arguments"))
                        .language(rs.getString("language") != null ? rs.getString("language") : "SQL")
                        .definition(rs.getString("definition"))
                        .build());
            }
        }

        return procedures;
    }

    // ─── Views ────────────────────────────────────────────────────────────────

    private List<ViewModel> extractViews(Connection conn, String dbName, String tableFilter) throws SQLException {
        List<ViewModel> views = new ArrayList<>();

        String sql = "SELECT TABLE_NAME, VIEW_DEFINITION " +
                     "FROM information_schema.VIEWS " +
                     "WHERE TABLE_SCHEMA = '" + dbName + "'" +
                     tableFilter +
                     " ORDER BY TABLE_NAME";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                views.add(ViewModel.builder()
                        .name(rs.getString("TABLE_NAME"))
                        .definition(rs.getString("VIEW_DEFINITION"))
                        .build());
            }
        }
        return views;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
