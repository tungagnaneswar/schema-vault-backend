package com.schemavault.app.service;

import com.schemavault.app.dto.diff.*;
import com.schemavault.app.dto.schema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaComparisonServiceTest {

    private SchemaComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = new SchemaComparisonService();
    }

    @Test
    void testEquivalentTypes_Identical() {
        assertTypeComparison("character varying", "varchar", DiffStatus.IDENTICAL);
        assertTypeComparison("int4", "integer", DiffStatus.IDENTICAL);
        assertTypeComparison("int8", "bigint", DiffStatus.IDENTICAL);
        assertTypeComparison("int2", "smallint", DiffStatus.IDENTICAL);
        assertTypeComparison("bool", "boolean", DiffStatus.IDENTICAL);
        assertTypeComparison("timestamp without time zone", "timestamp", DiffStatus.IDENTICAL);
        assertTypeComparison("timestamp with time zone", "timestamptz", DiffStatus.IDENTICAL);
    }

    @Test
    void testDifferentTypes_TypeMismatch() {
        assertTypeComparison("integer", "bigint", DiffStatus.TYPE_MISMATCH);
        assertTypeComparison("varchar", "text", DiffStatus.TYPE_MISMATCH);
    }

    @Test
    void testVarcharLengthDifferences() {
        ColumnModel src = ColumnModel.builder().name("name").type("varchar").maxLength(100).build();
        ColumnModel tgt = ColumnModel.builder().name("name").type("varchar").maxLength(255).build();

        SchemaDiffResponse response = compareCols(src, tgt);
        assertEquals(DiffStatus.LENGTH_MISMATCH, getColStatus(response));
        assertEquals(ChangeSeverity.REVIEW, getColSeverity(response));
    }

    @Test
    void testNullableDifferences() {
        ColumnModel src = ColumnModel.builder().name("name").type("varchar").isNullable(false).build();
        ColumnModel tgt = ColumnModel.builder().name("name").type("varchar").isNullable(true).build();

        SchemaDiffResponse response = compareCols(src, tgt);
        assertEquals(DiffStatus.NULLABILITY_MISMATCH, getColStatus(response));
        // tgt(true) -> src(false) is REVIEW (adding NOT NULL)
        assertEquals(ChangeSeverity.REVIEW, getColSeverity(response));
    }

    @Test
    void testDefaultDifferences() {
        ColumnModel src = ColumnModel.builder().name("name").type("integer").defaultValue("0").build();
        ColumnModel tgt = ColumnModel.builder().name("name").type("integer").defaultValue("1").build();

        SchemaDiffResponse response = compareCols(src, tgt);
        assertEquals(DiffStatus.DEFAULT_MISMATCH, getColStatus(response));
        assertEquals(ChangeSeverity.REVIEW, getColSeverity(response));
    }

    @Test
    void testDefaultNormalization() {
        ColumnModel src = ColumnModel.builder().name("name").type("boolean").defaultValue("'true'::boolean").build();
        ColumnModel tgt = ColumnModel.builder().name("name").type("boolean").defaultValue("true").build();

        SchemaDiffResponse response = compareCols(src, tgt);
        assertEquals(DiffStatus.IDENTICAL, getColStatus(response));
    }

    @Test
    void testSourceOnly_AddMigration() {
        TableModel table = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("integer").build()))
                .build();

        SchemaModel source = SchemaModel.builder().tables(List.of(table)).build();
        SchemaModel target = SchemaModel.builder().tables(List.of()).build();

        SchemaDiffResponse response = comparisonService.compareSchemas(source, target, "DEV", "PROD");

        assertEquals(1, response.getTableDiffs().size());
        TableDiff diff = response.getTableDiffs().get(0);
        assertEquals(DiffStatus.MISSING_IN_TARGET, diff.getStatus());
        assertEquals(MigrationOperation.ADD_TABLE, diff.getMigrationOperation());
        assertEquals(ChangeSeverity.SAFE, diff.getSeverity());
        assertEquals(1, diff.getColumnsSourceOnly());
    }

    @Test
    void testTargetOnly_DropMigration() {
        TableModel table = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("integer").build()))
                .build();

        SchemaModel source = SchemaModel.builder().tables(List.of()).build();
        SchemaModel target = SchemaModel.builder().tables(List.of(table)).build();

        SchemaDiffResponse response = comparisonService.compareSchemas(source, target, "DEV", "PROD");

        assertEquals(1, response.getTableDiffs().size());
        TableDiff diff = response.getTableDiffs().get(0);
        assertEquals(DiffStatus.MISSING_IN_SOURCE, diff.getStatus());
        assertEquals(MigrationOperation.DROP_TABLE, diff.getMigrationOperation());
        assertEquals(ChangeSeverity.DESTRUCTIVE, diff.getSeverity());
        assertEquals(1, diff.getColumnsTargetOnly());
        
        assertEquals(ChangeSeverity.DESTRUCTIVE, response.getSummary().getOverallRisk());
        assertEquals(1, response.getSummary().getDestructiveChanges());
    }

    @Test
    void testReverseComparison() {
        TableModel table1 = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("integer").build()))
                .build();
        
        TableModel table2 = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("bigint").build()))
                .build();

        SchemaModel schema1 = SchemaModel.builder().tables(List.of(table1)).build();
        SchemaModel schema2 = SchemaModel.builder().tables(List.of(table2)).build();

        SchemaDiffResponse response1 = comparisonService.compareSchemas(schema1, schema2, "DEV", "PROD");
        SchemaDiffResponse response2 = comparisonService.compareSchemas(schema2, schema1, "PROD", "DEV");

        assertEquals(DiffStatus.TYPE_MISMATCH, getColStatus(response1));
        assertEquals(DiffStatus.TYPE_MISMATCH, getColStatus(response2));
    }

    private void assertTypeComparison(String srcType, String tgtType, DiffStatus expectedStatus) {
        ColumnModel src = ColumnModel.builder().name("col").type(srcType).build();
        ColumnModel tgt = ColumnModel.builder().name("col").type(tgtType).build();
        SchemaDiffResponse response = compareCols(src, tgt);
        assertEquals(expectedStatus, getColStatus(response), "Failed for " + srcType + " vs " + tgtType);
    }

    private SchemaDiffResponse compareCols(ColumnModel src, ColumnModel tgt) {
        TableModel srcTable = TableModel.builder().name("t").columns(List.of(src)).build();
        TableModel tgtTable = TableModel.builder().name("t").columns(List.of(tgt)).build();
        return comparisonService.compareSchemas(
                SchemaModel.builder().tables(List.of(srcTable)).build(),
                SchemaModel.builder().tables(List.of(tgtTable)).build(),
                "DEV", "PROD"
        );
    }

    private DiffStatus getColStatus(SchemaDiffResponse response) {
        return response.getTableDiffs().get(0).getColumnDiffs().get(0).getStatus();
    }

    private ChangeSeverity getColSeverity(SchemaDiffResponse response) {
        return response.getTableDiffs().get(0).getColumnDiffs().get(0).getSeverity();
    }
}
