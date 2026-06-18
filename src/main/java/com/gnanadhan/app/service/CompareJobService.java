package com.gnanadhan.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnanadhan.app.dto.diff.SchemaDiffResponse;
import com.gnanadhan.app.dto.schema.SchemaModel;
import com.gnanadhan.app.entity.CompareJob;
import com.gnanadhan.app.entity.SchemaSnapshot;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.repository.CompareJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompareJobService {

    private final CompareJobRepository compareJobRepository;
    private final SnapshotService snapshotService;
    private final SchemaComparisonService comparisonService;
    private final ObjectMapper objectMapper;

    public CompareJob startJob(Long sourceSnapshotId, Long targetSnapshotId) {
        SchemaSnapshot sourceSnapshot = snapshotService.getSnapshotById(sourceSnapshotId);
        SchemaSnapshot targetSnapshot = snapshotService.getSnapshotById(targetSnapshotId);

        CompareJob job = CompareJob.builder()
                .status("PENDING")
                .sourceSnapshot(sourceSnapshot)
                .targetSnapshot(targetSnapshot)
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
                    sourceSnapshot.getConnection().getEnvironment(), 
                    targetSnapshot.getConnection().getEnvironment()
            );

            job.setResultData(objectMapper.writeValueAsString(diffResponse));
            job.setStatus("COMPLETED");
            job.setCompletedAt(ZonedDateTime.now());
        } catch (Exception e) {
            log.error("Failed to process job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(ZonedDateTime.now());
        } finally {
            compareJobRepository.save(job);
        }
    }

    public Page<CompareJob> getAllJobs(int page, int size) {
        return compareJobRepository.findAll(PageRequest.of(page, size));
    }

    public CompareJob getJobById(Long id) {
        return compareJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compare Job not found"));
    }
}
