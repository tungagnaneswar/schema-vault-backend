package com.schemavault.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class EnvironmentResponse {
    private Long id;
    private String name;
    private Long projectId;
    private Integer sequence;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
