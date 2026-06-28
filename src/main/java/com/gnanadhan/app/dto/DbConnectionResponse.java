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
public class DbConnectionResponse {
    private Long id;
    private String name;
    private Long environmentId;
    private String environmentName;
    private Long projectId;
    private String projectName;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;

    private String engine;
    private String createdBy;
    private String permissionLevel;
    private ZonedDateTime createdAt;
}
