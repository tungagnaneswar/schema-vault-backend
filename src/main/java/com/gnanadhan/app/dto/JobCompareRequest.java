package com.gnanadhan.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobCompareRequest {
    @NotNull
    private Long sourceSnapshotId;
    @NotNull
    private Long targetSnapshotId;
}
