package com.gnanadhan.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@Builder
@EqualsAndHashCode
public class IndexModel {
    private String name;
    private List<String> columns;
    private boolean isUnique;
    private String indexType;   // btree, hash, gin, gist, etc.
    private String definition;  // Full CREATE INDEX statement
}
