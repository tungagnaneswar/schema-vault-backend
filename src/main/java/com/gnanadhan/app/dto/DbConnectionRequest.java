package com.gnanadhan.app.dto;

import com.gnanadhan.app.entity.DatabaseEngine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DbConnectionRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Environment ID is required")
    private Long environmentId;

    @NotBlank(message = "Host is required")
    @Size(max = 255, message = "Host must not exceed 255 characters")
    private String host;

    @NotNull(message = "Port is required")
    private Integer port;

    @NotBlank(message = "Database name is required")
    @Size(max = 100, message = "Database name must not exceed 100 characters")
    private String databaseName;

    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must not exceed 100 characters")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;



    @NotNull(message = "Database engine is required")
    private DatabaseEngine engine;
}
