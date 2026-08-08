package com.schemavault.app.service.extractor;

import com.schemavault.app.dto.schema.*;
import com.schemavault.app.entity.DatabaseEngine;
import com.schemavault.app.entity.DbConnection;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class PostgresSchemaExtractor implements SchemaExtractor {

    @Override
    public boolean supports(DatabaseEngine engine) {
        return engine == DatabaseEngine.POSTGRES;
    }

    @Override
    public SchemaModel extract(DbConnection connection, String decryptedPassword) {
        String url = connection.getEngine().buildJdbcUrl(
                connection.getHost(), connection.getPort(), connection.getDatabaseName());

        try (Connection conn = DriverManager.getConnection(url, connection.getUsername(), decryptedPassword)) {

            String schemaFilter = buildSchemaFilter(connection.getIncludedSchemas());
            String tableFilter = buildTableFilter(connection.getExcludedTables(), "table_name");

            List<TableModel> tables = extractTables(conn, schemaFilter, tableFilter);
            List<FunctionModel> functions = extractFunctions(conn, schemaFilter);
            List<ProcedureModel> procedures = extractProcedures(conn, schemaFilter);
            List<SequenceModel> sequences = extractSequences(conn, schemaFilter);
            List<TypeModel> types = extractTypes(conn, schemaFilter);
            List<ViewModel> views = extractViews(conn, schemaFilter, tableFilter);

            return SchemaModel.builder()
                    .databaseName(connection.getDatabaseName())
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

    private String buildSchemaFilter(String includedSchemas) {
        if (includedSchemas == null || includedSchemas.trim().isEmpty()) {
            return " = 'public'";
        }
        String[] schemas = includedSchemas.split(",");
        StringBuilder sb = new StringBuilder(" IN (");
        for (int i = 0; i < schemas.length; i++) {
            sb.append("'").append(schemas[i].trim()).append("'");
            if (i < schemas.length - 1)
                sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildTableFilter(String excludedTables, String columnAlias) {
        if (excludedTables == null || excludedTables.trim().isEmpty()) {
            return "";
        }
        String[] tables = excludedTables.split(",");
        StringBuilder sb = new StringBuilder(" AND ").append(columnAlias).append(" NOT IN (");
        for (int i = 0; i < tables.length; i++) {
            sb.append("'").append(tables[i].trim()).append("'");
            if (i < tables.length - 1)
                sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    // ─── Tables ───────────────────────────────────────────────────────────────

    private List<TableModel> extractTables(Connection conn, String schemaFilter, String tableFilter)
            throws SQLException {
        List<TableModel> tables = new ArrayList<>();

        Map<String, List<ColumnModel>> allColumns = extractAllColumns(conn, schemaFilter);
        Map<String, List<String>> allPrimaryKeys = extractAllPrimaryKeys(conn, schemaFilter);
        Map<String, List<ConstraintModel>> allConstraints = extractAllConstraints(conn, schemaFilter);
        Map<String, List<ForeignKeyModel>> allForeignKeys = extractAllForeignKeys(conn, schemaFilter);
        Map<String, List<IndexModel>> allIndexes = extractAllIndexes(conn, schemaFilter);
        Map<String, List<TriggerModel>> allTriggers = extractAllTriggers(conn, schemaFilter);

        String sql = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema " + schemaFilter + " AND table_type = 'BASE TABLE' " +
                tableFilter +
                " ORDER BY table_name";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("table_name");
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

    private Map<String, List<ColumnModel>> extractAllColumns(Connection conn, String schemaFilter) throws SQLException {
        Map<String, List<ColumnModel>> map = new HashMap<>();

        String sql = "SELECT table_name, column_name, data_type, is_nullable, column_default, " +
                "ordinal_position, character_maximum_length, numeric_precision, numeric_scale " +
                "FROM information_schema.columns " +
                "WHERE table_schema " + schemaFilter + " " +
                "ORDER BY table_name, ordinal_position";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("table_name");
                map.computeIfAbsent(tableName, k -> new ArrayList<>()).add(ColumnModel.builder()
                        .name(rs.getString("column_name"))
                        .type(rs.getString("data_type"))
                        .isNullable("YES".equals(rs.getString("is_nullable")))
                        .defaultValue(rs.getString("column_default"))
                        .ordinalPosition(rs.getInt("ordinal_position"))
                        .maxLength(getIntOrNull(rs, "character_maximum_length"))
                        .numericPrecision(getIntOrNull(rs, "numeric_precision"))
                        .numericScale(getIntOrNull(rs, "numeric_scale"))
                        .build());
            }
        }

        return map;
    }

    // ─── Primary Keys ─────────────────────────────────────────────────────────

    private Map<String, List<String>> extractAllPrimaryKeys(Connection conn, String schemaFilter) throws SQLException {
        Map<String, List<String>> map = new HashMap<>();

        String sql = "SELECT tc.table_name, kcu.column_name " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name " +
                "  AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_schema " + schemaFilter + " " +
                "  AND tc.constraint_type = 'PRIMARY KEY' " +
                "ORDER BY tc.table_name, kcu.ordinal_position";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tableName = rs.getString("table_name");
                map.computeIfAbsent(tableName, k -> new ArrayList<>()).add(rs.getString("column_name"));
            }
        }

        return map;
    }

    // ─── Constraints (CHECK, UNIQUE, EXCLUDE) ─────────────────────────────────

    private Map<String, List<ConstraintModel>> extractAllConstraints(Connection conn, String schemaFilter)
            throws SQLException {
        Map<String, List<ConstraintModel>> map = new HashMap<>();

        // CHECK constraints
        String checkSql = "SELECT tc.table_name, tc.constraint_name, tc.constraint_type, cc.check_clause " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.check_constraints cc " +
                "  ON tc.constraint_name = cc.constraint_name " +
                "  AND tc.constraint_schema = cc.constraint_schema " +
                "WHERE tc.table_schema " + schemaFilter + " " +
                "  AND tc.constraint_type = 'CHECK' " +
                "ORDER BY tc.table_name, tc.constraint_name";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkSql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(ConstraintModel.builder()
                        .name(rs.getString("constraint_name"))
                        .type("CHECK")
                        .definition(rs.getString("check_clause"))
                        .build());
            }
        }

        // UNIQUE constraints
        String uniqueSql = "SELECT tc.table_name, tc.constraint_name, " +
                "string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS columns " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name " +
                "  AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.table_schema " + schemaFilter + " " +
                "  AND tc.constraint_type = 'UNIQUE' " +
                "GROUP BY tc.table_name, tc.constraint_name " +
                "ORDER BY tc.table_name, tc.constraint_name";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(uniqueSql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(ConstraintModel.builder()
                        .name(rs.getString("constraint_name"))
                        .type("UNIQUE")
                        .definition("UNIQUE (" + rs.getString("columns") + ")")
                        .build());
            }
        }

        // EXCLUDE constraints
        String excludeSql = "SELECT rel.relname AS table_name, con.conname AS constraint_name, " +
                "pg_get_constraintdef(con.oid) AS definition " +
                "FROM pg_catalog.pg_constraint con " +
                "JOIN pg_catalog.pg_class rel ON rel.oid = con.conrelid " +
                "JOIN pg_catalog.pg_namespace nsp ON nsp.oid = rel.relnamespace " +
                "WHERE nsp.nspname " + schemaFilter + " " +
                "  AND con.contype = 'x' " +
                "ORDER BY rel.relname, con.conname";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(excludeSql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(ConstraintModel.builder()
                        .name(rs.getString("constraint_name"))
                        .type("EXCLUDE")
                        .definition(rs.getString("definition"))
                        .build());
            }
        }

        return map;
    }

    // ─── Foreign Keys ─────────────────────────────────────────────────────────

    private Map<String, List<ForeignKeyModel>> extractAllForeignKeys(Connection conn, String schemaFilter)
            throws SQLException {
        Map<String, List<ForeignKeyModel>> map = new HashMap<>();

        String sql = "SELECT tc.table_name, tc.constraint_name, " +
                "  string_agg(DISTINCT kcu.column_name, ', ' ORDER BY kcu.column_name) AS columns, " +
                "  ccu.table_name AS referenced_table, " +
                "  string_agg(DISTINCT ccu.column_name, ', ' ORDER BY ccu.column_name) AS referenced_columns, " +
                "  rc.update_rule, rc.delete_rule " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu " +
                "  ON tc.constraint_name = kcu.constraint_name " +
                "  AND tc.table_schema = kcu.table_schema " +
                "JOIN information_schema.constraint_column_usage ccu " +
                "  ON tc.constraint_name = ccu.constraint_name " +
                "  AND tc.table_schema = ccu.table_schema " +
                "JOIN information_schema.referential_constraints rc " +
                "  ON tc.constraint_name = rc.constraint_name " +
                "  AND tc.table_schema = rc.constraint_schema " +
                "WHERE tc.table_schema " + schemaFilter + " " +
                "  AND tc.constraint_type = 'FOREIGN KEY' " +
                "GROUP BY tc.table_name, tc.constraint_name, ccu.table_name, rc.update_rule, rc.delete_rule " +
                "ORDER BY tc.table_name, tc.constraint_name";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(ForeignKeyModel.builder()
                        .name(rs.getString("constraint_name"))
                        .columns(Arrays.asList(rs.getString("columns").split(",\\s*")))
                        .referencedTable(rs.getString("referenced_table"))
                        .referencedColumns(Arrays.asList(rs.getString("referenced_columns").split(",\\s*")))
                        .updateRule(rs.getString("update_rule"))
                        .deleteRule(rs.getString("delete_rule"))
                        .build());
            }
        }

        return map;
    }

    // ─── Indexes ──────────────────────────────────────────────────────────────

    private Map<String, List<IndexModel>> extractAllIndexes(Connection conn, String schemaFilter) throws SQLException {
        Map<String, List<IndexModel>> map = new HashMap<>();

        String sql = "SELECT i.tablename AS table_name, i.indexname, i.indexdef, " +
                "  ix.indisunique, am.amname AS index_type, " +
                "  string_agg(a.attname, ', ' ORDER BY array_position(ix.indkey, a.attnum)) AS columns " +
                "FROM pg_indexes i " +
                "JOIN pg_class c ON c.relname = i.indexname " +
                "JOIN pg_index ix ON ix.indexrelid = c.oid " +
                "JOIN pg_am am ON am.oid = c.relam " +
                "JOIN pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = ANY(ix.indkey) " +
                "WHERE i.schemaname " + schemaFilter + " " +
                "  AND NOT EXISTS (" +
                "    SELECT 1 FROM pg_constraint con " +
                "    WHERE con.conindid = c.oid AND con.contype IN ('p', 'u')" +
                "  ) " +
                "GROUP BY i.tablename, i.indexname, i.indexdef, ix.indisunique, am.amname " +
                "ORDER BY i.tablename, i.indexname";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(IndexModel.builder()
                        .name(rs.getString("indexname"))
                        .columns(Arrays.asList(rs.getString("columns").split(",\\s*")))
                        .isUnique(rs.getBoolean("indisunique"))
                        .indexType(rs.getString("index_type"))
                        .definition(rs.getString("indexdef"))
                        .build());
            }
        }

        return map;
    }

    // ─── Triggers ─────────────────────────────────────────────────────────────

    private Map<String, List<TriggerModel>> extractAllTriggers(Connection conn, String schemaFilter)
            throws SQLException {
        Map<String, List<TriggerModel>> map = new HashMap<>();

        String sql = "SELECT event_object_table AS table_name, trigger_name, " +
                "  string_agg(DISTINCT event_manipulation, ', ' ORDER BY event_manipulation) AS event, " +
                "  action_timing, action_statement " +
                "FROM information_schema.triggers " +
                "WHERE trigger_schema " + schemaFilter + " " +
                "GROUP BY event_object_table, trigger_name, action_timing, action_statement " +
                "ORDER BY event_object_table, trigger_name";

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>()).add(TriggerModel.builder()
                        .name(rs.getString("trigger_name"))
                        .event(rs.getString("event"))
                        .timing(rs.getString("action_timing"))
                        .definition(rs.getString("action_statement"))
                        .build());
            }
        }

        return map;
    }

    // ─── Functions ────────────────────────────────────────────────────────────

    private List<FunctionModel> extractFunctions(Connection conn, String schemaFilter) throws SQLException {
        List<FunctionModel> functions = new ArrayList<>();

        String sql = "SELECT p.proname AS name, " +
                "  pg_get_function_result(p.oid) AS return_type, " +
                "  pg_get_function_identity_arguments(p.oid) AS arguments, " +
                "  l.lanname AS language, " +
                "  pg_get_functiondef(p.oid) AS definition " +
                "FROM pg_proc p " +
                "JOIN pg_namespace n ON n.oid = p.pronamespace " +
                "JOIN pg_language l ON l.oid = p.prolang " +
                "WHERE n.nspname " + schemaFilter + " AND p.prokind = 'f' " +
                "ORDER BY p.proname";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                functions.add(FunctionModel.builder()
                        .name(rs.getString("name"))
                        .returnType(rs.getString("return_type"))
                        .arguments(rs.getString("arguments"))
                        .language(rs.getString("language"))
                        .definition(rs.getString("definition"))
                        .build());
            }
        }

        return functions;
    }

    // ─── Procedures ───────────────────────────────────────────────────────────

    private List<ProcedureModel> extractProcedures(Connection conn, String schemaFilter) throws SQLException {
        List<ProcedureModel> procedures = new ArrayList<>();

        String sql = "SELECT p.proname AS name, " +
                "  pg_get_function_identity_arguments(p.oid) AS arguments, " +
                "  l.lanname AS language, " +
                "  pg_get_functiondef(p.oid) AS definition " +
                "FROM pg_proc p " +
                "JOIN pg_namespace n ON n.oid = p.pronamespace " +
                "JOIN pg_language l ON l.oid = p.prolang " +
                "WHERE n.nspname " + schemaFilter + " AND p.prokind = 'p' " +
                "ORDER BY p.proname";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                procedures.add(ProcedureModel.builder()
                        .name(rs.getString("name"))
                        .arguments(rs.getString("arguments"))
                        .language(rs.getString("language"))
                        .definition(rs.getString("definition"))
                        .build());
            }
        }

        return procedures;
    }

    // ─── Sequences ────────────────────────────────────────────────────────────

    private List<SequenceModel> extractSequences(Connection conn, String schemaFilter) throws SQLException {
        List<SequenceModel> sequences = new ArrayList<>();

        String sql = "SELECT sequence_name, data_type, start_value, increment, " +
                "  minimum_value, maximum_value, cycle_option " +
                "FROM information_schema.sequences " +
                "WHERE sequence_schema " + schemaFilter + " " +
                "ORDER BY sequence_name";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                sequences.add(SequenceModel.builder()
                        .name(rs.getString("sequence_name"))
                        .dataType(rs.getString("data_type"))
                        .startValue(rs.getLong("start_value"))
                        .increment(rs.getLong("increment"))
                        .minValue(rs.getLong("minimum_value"))
                        .maxValue(rs.getLong("maximum_value"))
                        .isCyclic("YES".equals(rs.getString("cycle_option")))
                        .build());
            }
        }

        return sequences;
    }

    // ─── Types ────────────────────────────────────────────────────────────────

    private List<TypeModel> extractTypes(Connection conn, String schemaFilter) throws SQLException {
        List<TypeModel> types = new ArrayList<>();

        String sql = "SELECT n.nspname as schema, t.typname as name, " +
                "  CASE t.typtype " +
                "    WHEN 'b' THEN 'BASE' " +
                "    WHEN 'c' THEN 'COMPOSITE' " +
                "    WHEN 'd' THEN 'DOMAIN' " +
                "    WHEN 'e' THEN 'ENUM' " +
                "    WHEN 'p' THEN 'PSEUDO' " +
                "    WHEN 'r' THEN 'RANGE' " +
                "    WHEN 'm' THEN 'MULTIRANGE' " +
                "    ELSE t.typtype::text " +
                "  END as type_type, " +
                "  pg_catalog.obj_description(t.oid, 'pg_type') as description, " +
                "  (SELECT string_agg(e.enumlabel, ', ' ORDER BY e.enumsortorder) " +
                "   FROM pg_catalog.pg_enum e WHERE e.enumtypid = t.oid) as enum_values " +
                "FROM pg_catalog.pg_type t " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace " +
                "WHERE n.nspname " + schemaFilter + " " +
                "  AND t.typtype IN ('e', 'c', 'd') " + // ENUM, COMPOSITE, DOMAIN
                "ORDER BY t.typname";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String typeName = rs.getString("name");
                String typeCategory = rs.getString("type_type");
                String enumValues = rs.getString("enum_values");

                String definition = "";
                if ("ENUM".equals(typeCategory) && enumValues != null) {
                    definition = "ENUM (" + enumValues + ")";
                } else {
                    definition = typeCategory; // simplified for composite/domain
                }

                types.add(TypeModel.builder()
                        .name(typeName)
                        .type(typeCategory)
                        .definition(definition)
                        .build());
            }
        }
        return types;
    }

    // ─── Views ────────────────────────────────────────────────────────────────

    private List<ViewModel> extractViews(Connection conn, String schemaFilter, String tableFilter) throws SQLException {
        List<ViewModel> views = new ArrayList<>();

        String sql = "SELECT table_name, view_definition " +
                "FROM information_schema.views " +
                "WHERE table_schema " + schemaFilter + " " +
                tableFilter +
                " ORDER BY table_name";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                views.add(ViewModel.builder()
                        .name(rs.getString("table_name"))
                        .definition(rs.getString("view_definition"))
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
