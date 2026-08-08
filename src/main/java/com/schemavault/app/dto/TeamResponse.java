package com.schemavault.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {
    private Long id;
    private String name;
    private String description;
    private Long createdById;
    private String createdByEmail;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
