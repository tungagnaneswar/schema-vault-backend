package com.gnanadhan.app.mapper;

import com.gnanadhan.app.dto.EnvironmentResponse;
import com.gnanadhan.app.entity.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentMapper {

    public EnvironmentResponse toResponse(Environment environment) {
        if (environment == null) {
            return null;
        }

        return EnvironmentResponse.builder()
                .id(environment.getId())
                .name(environment.getName())
                .projectId(environment.getProject().getId())
                .sequence(environment.getSequence())
                .createdAt(environment.getCreatedAt())
                .updatedAt(environment.getUpdatedAt())
                .build();
    }
}
