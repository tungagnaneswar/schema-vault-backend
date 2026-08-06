package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.DashboardResponse;
import com.gnanadhan.app.dto.RecentCompareJobDto;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.repository.CompareJobRepository;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.TeamRepository;
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
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStats() {
        User user = currentUserService.getCurrentUser();
        boolean isSuperAdmin = user.getRole().getName().equals("SUPER_ADMIN");
        
        long activeConnections;
        if (isSuperAdmin) {
            activeConnections = dbConnectionRepository.count();
        } else {
            activeConnections = dbConnectionRepository.findAccessibleConnections(user.getId(), PageRequest.of(0, 1)).getTotalElements();
        }

        long schemasCompared;
        List<RecentCompareJobDto> recentComparisons;

        if (isSuperAdmin) {
            schemasCompared = compareJobRepository.count();
            recentComparisons = compareJobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 5))
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
        } else {
            schemasCompared = compareJobRepository.countAccessibleJobs(user.getId());
            recentComparisons = compareJobRepository.findAccessibleJobs(user.getId(), PageRequest.of(0, 5))
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
        }

        long activeUsers = isSuperAdmin ? userRepository.count() : 0;
        long teams = isSuperAdmin ? teamRepository.count() : teamRepository.countAccessibleTeams(user.getId());
        long systemAlerts = 0; // Static 0 for now as per plan

        return DashboardResponse.builder()
                .activeConnections(activeConnections)
                .schemasCompared(schemasCompared)
                .systemAlerts(systemAlerts)
                .activeUsers(activeUsers)
                .teams(teams)
                .recentComparisons(recentComparisons)
                .build();
    }
}
