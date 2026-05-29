package com.gnanadhan.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class FunctionModel {
    private String name;
    private String returnType;
    private String arguments;   // Full argument signature
    private String language;    // plpgsql, sql, etc.
    private String definition;  // Function body
}
