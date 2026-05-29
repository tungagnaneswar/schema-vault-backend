package com.gnanadhan.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class ColumnModel {
    private String name;
    private String type;
    private boolean isNullable;
    private String defaultValue;
    private int ordinalPosition;
    private Integer maxLength;
    private Integer numericPrecision;
    private Integer numericScale;
}
