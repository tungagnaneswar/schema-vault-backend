package com.gnanadhan.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private Long createdById;
    private String createdByEmail;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private int connectionCount;
}
