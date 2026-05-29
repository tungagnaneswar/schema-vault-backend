package com.gnanadhan.app.dto.diff;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompareRequest {
    @NotNull(message = "Source connection ID is required")
    private Long sourceConnectionId;

    @NotNull(message = "Target connection ID is required")
    private Long targetConnectionId;
}
