package com.gnanadhan.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDbConnectionRequest {
    @NotNull(message = "Database connection ID is required")
    private Long dbConnectionId;

    @NotBlank(message = "Permission level is required")
    private String permissionLevel; // 'READ', 'WRITE', 'ADMIN'
}
