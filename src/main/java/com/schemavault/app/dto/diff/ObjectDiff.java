package com.schemavault.app.dto.diff;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ObjectDiff {

    private String name;
    private DiffStatus status;
    private String sourceDefinition;
    private String targetDefinition;
    private List<String> mismatchDetails;
    private String objectType;
    private MigrationOperation migrationOperation;
    private ChangeSeverity severity;
}
