package com.gnanadhan.app.dto.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TypeModel {
    private String name;
    private String type; // e.g. ENUM, COMPOSITE, DOMAIN
    private String definition; // For enums, list of values; for composites, list of attributes, etc.
}
