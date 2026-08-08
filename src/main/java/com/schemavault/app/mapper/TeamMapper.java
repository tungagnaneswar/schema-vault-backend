package com.schemavault.app.mapper;

import com.schemavault.app.dto.TeamRequest;
import com.schemavault.app.dto.TeamResponse;
import com.schemavault.app.entity.Team;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper {

    public Team toEntity(TeamRequest request) {
        return Team.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
    }

    public TeamResponse toResponse(Team team) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .createdById(team.getCreatedBy() != null ? team.getCreatedBy().getId() : null)
                .createdByEmail(team.getCreatedBy() != null ? team.getCreatedBy().getEmail() : null)
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }
}
