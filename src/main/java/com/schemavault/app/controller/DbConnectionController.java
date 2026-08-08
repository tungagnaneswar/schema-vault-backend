package com.schemavault.app.controller;

import com.schemavault.app.dto.DbConnectionRequest;
import com.schemavault.app.dto.DbConnectionResponse;
import com.schemavault.app.dto.DbConnectionUpdateRequest;
import com.schemavault.app.service.DbConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class DbConnectionController {

    private final DbConnectionService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<DbConnectionResponse> createConnection(@Valid @RequestBody DbConnectionRequest request) {
        return new ResponseEntity<>(service.createConnection(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<DbConnectionResponse> updateConnection(@PathVariable Long id,
            @Valid @RequestBody DbConnectionUpdateRequest request) {
        return ResponseEntity.ok(service.updateConnection(id, request));
    }

    @GetMapping
    public ResponseEntity<Page<DbConnectionResponse>> getAllConnections(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.getAllConnections(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DbConnectionResponse> getConnectionById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getConnectionById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> deleteConnection(@PathVariable Long id) {
        service.deleteConnection(id);
        return ResponseEntity.ok(Map.of("message", "Connection deleted successfully"));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testConnection(@PathVariable Long id) {
        service.testSavedConnection(id);
        return ResponseEntity.ok(Map.of("message", "Connection test successful"));
    }
}
