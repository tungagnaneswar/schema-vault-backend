package com.schemavault.app.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class CompareJobResponse {
    private Long id;
    private String status;
    private Long sourceSnapshotId;
    private Long targetSnapshotId;
    private Long projectId;
    private Long createdById;
    private String createdByEmail;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private Long durationMs;
    private String errorMessage;
    private String reason;

    @JsonRawValue
    private String tags;

    @JsonRawValue
    private String summaryStatistics;

    @JsonRawValue
    private String resultData;
}
