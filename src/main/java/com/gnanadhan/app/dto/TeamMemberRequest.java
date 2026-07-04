package com.gnanadhan.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    private String teamRole; // Optional, defaults to TEAM_MEMBER in entity
}
