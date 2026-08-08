package com.schemavault.app.dto.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViewModel {
    private String name;
    private String definition; // SQL statement defining the view
}
