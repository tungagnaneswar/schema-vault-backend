package com.schemavault.app.dto.schema;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class TriggerModel {
    private String name;
    private String event; // INSERT, UPDATE, DELETE, or combined
    private String timing; // BEFORE, AFTER, INSTEAD OF
    private String definition; // Full trigger body / action statement
}
