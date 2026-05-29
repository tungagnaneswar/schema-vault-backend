package com.gnanadhan.app.dto;

import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private Long id;
    private String userEmail;
    private String action;
    private String ipAddress;
    private String deviceInfo;
    private ZonedDateTime timestamp;
    private String details;
}
