package com.gnanadhan.app.dto.diff;

import com.gnanadhan.app.dto.schema.ColumnModel;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ColumnDiff {
    private String columnName;
    private DiffStatus status;
    private ColumnModel sourceColumn;
    private ColumnModel targetColumn;
    private List<String> mismatchDetails;
}
