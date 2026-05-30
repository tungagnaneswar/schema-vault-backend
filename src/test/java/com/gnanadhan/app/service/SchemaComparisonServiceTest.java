package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.diff.DiffStatus;
import com.gnanadhan.app.dto.diff.SchemaDiffResponse;
import com.gnanadhan.app.dto.schema.ColumnModel;
import com.gnanadhan.app.dto.schema.SchemaModel;
import com.gnanadhan.app.dto.schema.TableModel;
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
    void compareSchemas_WithMissingTable() {
        TableModel table1 = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("integer").build()))
                .build();
                
        SchemaModel source = SchemaModel.builder().tables(List.of(table1)).build();
        SchemaModel target = SchemaModel.builder().tables(List.of()).build();

        SchemaDiffResponse response = comparisonService.compareSchemas(source, target, "DEV", "PROD");

        assertEquals(1, response.getTableDiffs().size());
        assertEquals("users", response.getTableDiffs().get(0).getTableName());
        assertEquals(DiffStatus.MISSING_IN_TARGET, response.getTableDiffs().get(0).getStatus());
    }
    
    @Test
    void compareSchemas_WithDefinitionMismatch() {
        TableModel sourceTable = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("integer").build()))
                .build();
                
        TableModel targetTable = TableModel.builder()
                .name("users")
                .columns(List.of(ColumnModel.builder().name("id").type("bigint").build()))
                .build();
                
        SchemaModel source = SchemaModel.builder().tables(List.of(sourceTable)).build();
        SchemaModel target = SchemaModel.builder().tables(List.of(targetTable)).build();

        SchemaDiffResponse response = comparisonService.compareSchemas(source, target, "DEV", "PROD");

        assertEquals(1, response.getTableDiffs().size());
        assertEquals(DiffStatus.TYPE_MISMATCH, response.getTableDiffs().get(0).getStatus());
        assertEquals(1, response.getTableDiffs().get(0).getColumnDiffs().size());
    }
}
