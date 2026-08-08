package com.schemavault.app.dto.diff;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SchemaDiffResponse {
    private String sourceEnvironment;
    private String targetEnvironment;
    private List<TableDiff> tableDiffs;
    private List<ObjectDiff> functionDiffs;
    private List<ObjectDiff> procedureDiffs;
    private List<ObjectDiff> sequenceDiffs;
    private List<ObjectDiff> typeDiffs;
    private List<ObjectDiff> viewDiffs;
}
