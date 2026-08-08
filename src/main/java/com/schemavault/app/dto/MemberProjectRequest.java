package com.schemavault.app.dto;

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
public class MemberProjectRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Permission is required")
    private String permission; // 'READ', 'WRITE', 'ADMIN'
}
