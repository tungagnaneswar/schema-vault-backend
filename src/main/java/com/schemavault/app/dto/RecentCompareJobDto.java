package com.schemavault.app.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentCompareJobDto {
    private Long id;
    private String status;
    private String projectName;
    private String sourceEnvironmentName;
    private String targetEnvironmentName;
    private String createdByEmail;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private Long durationMs;
    @JsonRawValue
    private String summaryStatistics;
}
