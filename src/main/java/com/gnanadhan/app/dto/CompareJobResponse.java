package com.gnanadhan.app.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

@Data
@Builder
public class CompareJobResponse {
    private Long id;
    private String status;
    private Long sourceSnapshotId;
    private Long targetSnapshotId;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private String errorMessage;
    
    @JsonRawValue
    private String resultData;
}
