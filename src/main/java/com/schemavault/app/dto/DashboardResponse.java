package com.schemavault.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private long activeConnections;
    private long schemasCompared;
    private long systemAlerts;
    private long activeUsers;
    private long teams;
    private java.util.List<RecentCompareJobDto> recentComparisons;
}
