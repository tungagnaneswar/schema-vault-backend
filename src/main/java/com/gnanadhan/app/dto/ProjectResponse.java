package com.gnanadhan.app.dto;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
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
