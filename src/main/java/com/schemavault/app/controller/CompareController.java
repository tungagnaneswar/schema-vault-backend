package com.schemavault.app.controller;

import com.schemavault.app.dto.JobCompareRequest;
import com.schemavault.app.dto.CompareJobResponse;
import com.schemavault.app.entity.CompareJob;
import com.schemavault.app.entity.Project;
import com.schemavault.app.service.CompareJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compare/jobs")
@RequiredArgsConstructor
public class CompareController {

    private final CompareJobService compareJobService;

    @PostMapping
    public ResponseEntity<CompareJobResponse> createJob(@Valid @RequestBody JobCompareRequest request) {
        CompareJob job = compareJobService.startJob(request);
        compareJobService.processJob(job.getId()); // async call
        return ResponseEntity.accepted().body(mapToResponse(job));
    }

    @GetMapping
    public ResponseEntity<Page<CompareJobResponse>> getJobs(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<CompareJob> jobs;
        if (projectId != null) {
            jobs = compareJobService.getJobsByProject(projectId, page, size);
        } else {
            jobs = compareJobService.getAllJobs(page, size);
        }
        return ResponseEntity.ok(jobs.map(this::mapToResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompareJobResponse> getJobById(@PathVariable Long id) {
        CompareJob job = compareJobService.getJobById(id);
        return ResponseEntity.ok(mapToResponse(job));
    }

    private CompareJobResponse mapToResponse(CompareJob job) {
        return CompareJobResponse.builder()
                .id(job.getId())
                .status(job.getStatus())
                .sourceSnapshotId(job.getSourceSnapshot().getId())
                .targetSnapshotId(job.getTargetSnapshot().getId())
                .projectId(job.getProject() != null ? job.getProject().getId() : null)
                .createdById(job.getCreatedBy() != null ? job.getCreatedBy().getId() : null)
                .createdByEmail(job.getCreatedBy() != null ? job.getCreatedBy().getEmail() : null)
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .durationMs(job.getDurationMs())
                .errorMessage(job.getErrorMessage())
                .reason(job.getReason())
                .tags(job.getTags())
                .summaryStatistics(job.getSummaryStatistics())
                .resultData("COMPLETED".equals(job.getStatus()) ? job.getResultData() : null)
                .build();
    }
}
