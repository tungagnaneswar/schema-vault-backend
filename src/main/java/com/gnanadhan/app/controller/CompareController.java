package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.JobCompareRequest;
import com.gnanadhan.app.dto.CompareJobResponse;
import com.gnanadhan.app.entity.CompareJob;
import com.gnanadhan.app.service.CompareJobService;
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
        CompareJob job = compareJobService.startJob(request.getSourceSnapshotId(), request.getTargetSnapshotId());
        compareJobService.processJob(job.getId()); // async call
        return ResponseEntity.accepted().body(mapToResponse(job));
    }

    @GetMapping
    public ResponseEntity<Page<CompareJobResponse>> getJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<CompareJob> jobs = compareJobService.getAllJobs(page, size);
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
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .resultData("COMPLETED".equals(job.getStatus()) ? job.getResultData() : null)
                .build();
    }
}
