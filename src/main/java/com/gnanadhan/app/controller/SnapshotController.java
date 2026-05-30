package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.SnapshotResponse;
import com.gnanadhan.app.entity.SchemaSnapshot;
import com.gnanadhan.app.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    @PostMapping("/connection/{connectionId}")
    public ResponseEntity<SnapshotResponse> createSnapshot(@PathVariable Long connectionId) {
        SchemaSnapshot snapshot = snapshotService.createSnapshot(connectionId);
        return ResponseEntity.ok(mapToResponse(snapshot));
    }

    @GetMapping("/connection/{connectionId}")
    public ResponseEntity<Page<SnapshotResponse>> getSnapshots(
            @PathVariable Long connectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<SchemaSnapshot> snapshots = snapshotService.getSnapshotsByConnection(connectionId, page, size);
        return ResponseEntity.ok(snapshots.map(this::mapToResponse));
    }

    private SnapshotResponse mapToResponse(SchemaSnapshot snapshot) {
        return SnapshotResponse.builder()
                .id(snapshot.getId())
                .connectionId(snapshot.getConnection().getId())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }
}
