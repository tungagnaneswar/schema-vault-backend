package com.schemavault.app.controller;

import com.schemavault.app.dto.EnvironmentRequest;
import com.schemavault.app.dto.EnvironmentResponse;
import com.schemavault.app.service.EnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<EnvironmentResponse>> getEnvironmentsByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(environmentService.getEnvironmentsByProject(projectId));
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> createEnvironment(@Valid @RequestBody EnvironmentRequest request) {
        return new ResponseEntity<>(environmentService.createEnvironment(request), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable Long id) {
        environmentService.deleteEnvironment(id);
        return ResponseEntity.noContent().build();
    }
}
