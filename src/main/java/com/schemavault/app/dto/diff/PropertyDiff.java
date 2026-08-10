package com.schemavault.app.dto.diff;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PropertyDiff {
    private String property;
    private String sourceValue;
    private String targetValue;
    private DiffStatus status;
    private ChangeSeverity severity;
    private String explanation;
}
