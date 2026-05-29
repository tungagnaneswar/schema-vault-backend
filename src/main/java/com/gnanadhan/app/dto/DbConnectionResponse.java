package com.gnanadhan.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class DbConnectionResponse {
    private Long id;
    private String name;
    private Long projectId;
    private String projectName;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String environment;
    private String createdBy;
    private String permissionLevel;
    private ZonedDateTime createdAt;
}
