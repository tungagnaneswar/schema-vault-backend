package com.schemavault.app.dto.diff;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TableDiff {
    private String tableName;
    private DiffStatus status;
    private List<ColumnDiff> columnDiffs;
    private PrimaryKeyDiff primaryKeyDiff;
    private List<ObjectDiff> constraintDiffs;
    private List<ObjectDiff> foreignKeyDiffs;
    private List<ObjectDiff> indexDiffs;
    private List<ObjectDiff> triggerDiffs;
}
