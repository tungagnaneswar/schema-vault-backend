package com.schemavault.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@Builder
@EqualsAndHashCode
public class ForeignKeyModel {
    private String name;
    private List<String> columns;
    private String referencedTable;
    private List<String> referencedColumns;
    private String updateRule; // CASCADE, SET NULL, NO ACTION, etc.
    private String deleteRule;
}
