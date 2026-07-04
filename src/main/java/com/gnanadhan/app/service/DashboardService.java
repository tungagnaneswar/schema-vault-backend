package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.DashboardResponse;
import com.gnanadhan.app.dto.RecentCompareJobDto;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.repository.CompareJobRepository;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.UserRepository;
import com.gnanadhan.app.service.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CurrentUserService currentUserService;
    private final DbConnectionRepository dbConnectionRepository;
    private final CompareJobRepository compareJobRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStats() {
        User user = currentUserService.getCurrentUser();
        
        long activeConnections;
        if (user.getRole().getName().equals("SUPER_ADMIN")) {
            activeConnections = dbConnectionRepository.count();
        } else {
            activeConnections = dbConnectionRepository.findAccessibleConnections(user.getId(), PageRequest.of(0, 1)).getTotalElements();
        }

        long schemasCompared = compareJobRepository.count(); // Global count for now
        long activeUsers = userRepository.count();
        long systemAlerts = 0; // Static 0 for now as per plan
        
        List<RecentCompareJobDto> recentComparisons = compareJobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 5))
                .getContent()
                .stream()
                .map(job -> RecentCompareJobDto.builder()
                        .id(job.getId())
                        .status(job.getStatus())
                        .projectName(job.getProject() != null ? job.getProject().getName() : null)
                        .sourceEnvironmentName(job.getSourceSnapshot().getConnection().getEnvironment().getName())
                        .targetEnvironmentName(job.getTargetSnapshot().getConnection().getEnvironment().getName())
                        .createdByEmail(job.getCreatedBy() != null ? job.getCreatedBy().getEmail() : null)
                        .startedAt(job.getStartedAt())
                        .completedAt(job.getCompletedAt())
                        .durationMs(job.getDurationMs())
                        .summaryStatistics(job.getSummaryStatistics())
                        .build())
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .activeConnections(activeConnections)
                .schemasCompared(schemasCompared)
                .systemAlerts(systemAlerts)
                .activeUsers(activeUsers)
                .recentComparisons(recentComparisons)
                .build();
    }
}
