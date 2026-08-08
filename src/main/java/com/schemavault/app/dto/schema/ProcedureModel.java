package com.schemavault.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class ProcedureModel {
    private String name;
    private String arguments; // Full argument signature
    private String language; // plpgsql, sql, etc.
    private String definition; // Procedure body
}
