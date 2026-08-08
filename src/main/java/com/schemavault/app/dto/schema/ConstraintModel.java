package com.schemavault.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class ConstraintModel {
    private String name;
    private String type; // CHECK, UNIQUE, EXCLUDE
    private String definition; // Full constraint expression
}
