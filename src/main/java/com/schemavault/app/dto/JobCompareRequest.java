package com.schemavault.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class JobCompareRequest {
    @NotNull
    private Long sourceSnapshotId;
    @NotNull
    private Long targetSnapshotId;
    @NotNull
    private Long projectId;
    private String reason;
    private List<String> tags;
}
