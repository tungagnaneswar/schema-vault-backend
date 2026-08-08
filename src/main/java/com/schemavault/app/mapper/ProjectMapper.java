package com.schemavault.app.mapper;

import com.schemavault.app.dto.ProjectRequest;
import com.schemavault.app.dto.ProjectResponse;
import com.schemavault.app.entity.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public Project toEntity(ProjectRequest request) {
        if (request == null) {
            return null;
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        return project;
    }

    public ProjectResponse toResponse(Project entity) {
        if (entity == null) {
            return null;
        }

        ProjectResponse response = new ProjectResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());

        if (entity.getCreatedBy() != null) {
            response.setCreatedById(entity.getCreatedBy().getId());
            response.setCreatedByEmail(entity.getCreatedBy().getEmail());
        }

        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        int connectionCount = 0;
        if (entity.getEnvironments() != null) {
            connectionCount = entity.getEnvironments().stream()
                    .mapToInt(e -> e.getConnections() != null ? e.getConnections().size() : 0)
                    .sum();
        }
        response.setConnectionCount(connectionCount);

        return response;
    }
}
