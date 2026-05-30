package com.gnanadhan.app.dto;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

@Data
@Builder
public class SnapshotResponse {
    private Long id;
    private Long connectionId;
    private ZonedDateTime createdAt;
}
