package com.schemavault.app.dto;

import com.schemavault.app.entity.DatabaseEngine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DbConnectionUpdateRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    private Integer port;

    @NotBlank(message = "Database name is required")
    private String databaseName;

    @NotBlank(message = "Username is required")
    private String username;

    // Optional on update - leave blank to keep existing password
    private String password;

    @NotNull(message = "Environment ID is required")
    private Long environmentId;

    @NotNull(message = "Database engine is required")
    private DatabaseEngine engine;
}
