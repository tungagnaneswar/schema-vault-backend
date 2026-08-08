package com.schemavault.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemavault.app.dto.JobCompareRequest;
import com.schemavault.app.dto.diff.SchemaDiffResponse;
import com.schemavault.app.dto.schema.SchemaModel;
import com.schemavault.app.entity.CompareJob;
import com.schemavault.app.entity.SchemaSnapshot;
import com.schemavault.app.entity.Team;
import com.schemavault.app.entity.User;
import com.schemavault.app.exception.ResourceNotFoundException;
import com.schemavault.app.entity.Project;
import com.schemavault.app.repository.CompareJobRepository;
import com.schemavault.app.repository.ProjectRepository;
import com.schemavault.app.service.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompareJobService {

    private final CompareJobRepository compareJobRepository;
    private final SnapshotService snapshotService;
    private final SchemaComparisonService comparisonService;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;

    public CompareJob startJob(JobCompareRequest request) {
        SchemaSnapshot sourceSnapshot = snapshotService.getSnapshotById(request.getSourceSnapshotId());
        SchemaSnapshot targetSnapshot = snapshotService.getSnapshotById(request.getTargetSnapshotId());

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.checkProjectAccess(project);
        User currentUser = currentUserService.getCurrentUser();

        String tagsJson = null;
        try {
            if (request.getTags() != null && !request.getTags().isEmpty()) {
                tagsJson = objectMapper.writeValueAsString(request.getTags());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize tags", e);
        }

        CompareJob job = CompareJob.builder()
                .status("PENDING")
                .sourceSnapshot(sourceSnapshot)
                .targetSnapshot(targetSnapshot)
                .project(project)
                .createdBy(currentUser)
                .reason(request.getReason())
                .tags(tagsJson)
                .build();

        return compareJobRepository.save(job);
    }

    @Async
    public void processJob(Long jobId) {
        CompareJob job = compareJobRepository.findByIdWithSnapshots(jobId).orElseThrow();

        try {
            job.setStatus("IN_PROGRESS");
            compareJobRepository.save(job);

            SchemaSnapshot sourceSnapshot = job.getSourceSnapshot();
            SchemaSnapshot targetSnapshot = job.getTargetSnapshot();

            SchemaModel sourceSchema = objectMapper.readValue(sourceSnapshot.getSnapshotData(), SchemaModel.class);
            SchemaModel targetSchema = objectMapper.readValue(targetSnapshot.getSnapshotData(), SchemaModel.class);

            SchemaDiffResponse diffResponse = comparisonService.compareSchemas(
                    sourceSchema,
                    targetSchema,
                    sourceSnapshot.getConnection().getEnvironment().getName(),
                    targetSnapshot.getConnection().getEnvironment().getName());

            job.setResultData(objectMapper.writeValueAsString(diffResponse));
            job.setStatus("COMPLETED");
            job.setCompletedAt(ZonedDateTime.now());

            if (job.getStartedAt() != null) {
                job.setDurationMs(Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis());
            }

            // Generate basic summary statistics
            Map<String, Integer> summary = new HashMap<>();
            int tablesAdded = 0;
            int tablesRemoved = 0;
            int tablesModified = 0;

            if (diffResponse.getTableDiffs() != null) {
                for (com.schemavault.app.dto.diff.TableDiff td : diffResponse.getTableDiffs()) {
                    if (td.getStatus() == com.schemavault.app.dto.diff.DiffStatus.MISSING_IN_TARGET)
                        tablesAdded++;
                    else if (td.getStatus() == com.schemavault.app.dto.diff.DiffStatus.MISSING_IN_SOURCE)
                        tablesRemoved++;
                    else
                        tablesModified++;
                }
            }

            summary.put("tablesAdded", tablesAdded);
            summary.put("tablesRemoved", tablesRemoved);
            summary.put("tablesModified", tablesModified);
            job.setSummaryStatistics(objectMapper.writeValueAsString(summary));

        } catch (Exception e) {
            log.error("Failed to process job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(ZonedDateTime.now());
            if (job.getStartedAt() != null) {
                job.setDurationMs(Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis());
            }
        } finally {
            compareJobRepository.save(job);
        }
    }

    public Page<CompareJob> getAllJobs(int page, int size) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole().getName().equals("SUPER_ADMIN")) {
            return compareJobRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
        } else {
            return compareJobRepository.findAccessibleJobs(currentUser.getId(), PageRequest.of(page, size));
        }
    }

    public Page<CompareJob> getJobsByProject(Long projectId, int page, int size) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectService.checkProjectAccess(project);
        return compareJobRepository.findByProjectIdOrderByStartedAtDesc(projectId, PageRequest.of(page, size));
    }

    public CompareJob getJobById(Long id) {
        CompareJob job = compareJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compare Job not found"));
        User currentUser = currentUserService.getCurrentUser();
        if (!currentUser.getRole().getName().equals("SUPER_ADMIN")) {
            if (job.getProject() != null) {
                projectService.checkProjectAccess(job.getProject());
            } else if (job.getCreatedBy() != null && !job.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You do not have permission to access this compare job");
            }
        }
        return job;
    }
}
