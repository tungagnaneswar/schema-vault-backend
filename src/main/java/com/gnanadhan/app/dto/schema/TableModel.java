package com.gnanadhan.app.dto.schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TableModel {
    private String name;
    private List<ColumnModel> columns;
    private List<String> primaryKeys;
    private List<ConstraintModel> constraints;
    private List<ForeignKeyModel> foreignKeys;
    private List<IndexModel> indexes;
    private List<TriggerModel> triggers;
}
