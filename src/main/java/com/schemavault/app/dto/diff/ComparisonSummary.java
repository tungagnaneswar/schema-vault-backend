package com.schemavault.app.dto.diff;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComparisonSummary {

    private int tablesChecked;

    private int tablesIdentical;

    private int tablesSourceOnly;

    private int tablesTargetOnly;

    private int tablesModified;

    private int columnsSourceOnly;

    private int columnsTargetOnly;

    private int columnsModified;

    private int safeChanges;

    private int reviewChanges;

    private int destructiveChanges;

    private ChangeSeverity overallRisk;

    private int functionDiffs;
    
    private int procedureDiffs;
    
    private int sequenceDiffs;
    
    private int typeDiffs;
    
    private int viewDiffs;
}
