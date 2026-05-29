package com.gnanadhan.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class SequenceModel {
    private String name;
    private String dataType;
    private Long startValue;
    private Long increment;
    private Long minValue;
    private Long maxValue;
    private boolean isCyclic;
}
