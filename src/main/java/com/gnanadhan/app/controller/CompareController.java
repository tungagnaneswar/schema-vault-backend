package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.diff.CompareRequest;
import com.gnanadhan.app.dto.diff.SchemaDiffResponse;
import com.gnanadhan.app.dto.schema.SchemaModel;
import com.gnanadhan.app.entity.DbConnection;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.service.SchemaComparisonService;
import com.gnanadhan.app.service.SchemaExtractionService;
import com.gnanadhan.app.util.EncryptionUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class CompareController {

    private final DbConnectionRepository dbConnectionRepository;
    private final SchemaExtractionService extractionService;
    private final SchemaComparisonService comparisonService;
    private final EncryptionUtil encryptionUtil;

    @PostMapping
    public ResponseEntity<SchemaDiffResponse> compare(@Valid @RequestBody CompareRequest request) {
        DbConnection sourceConn = dbConnectionRepository.findById(request.getSourceConnectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Source connection not found"));
        
        DbConnection targetConn = dbConnectionRepository.findById(request.getTargetConnectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Target connection not found"));

        SchemaModel sourceSchema = extractionService.extractSchema(
                sourceConn.getHost(),
                sourceConn.getPort(),
                sourceConn.getDatabaseName(),
                sourceConn.getUsername(),
                encryptionUtil.decrypt(sourceConn.getEncryptedPassword())
        );

        SchemaModel targetSchema = extractionService.extractSchema(
                targetConn.getHost(),
                targetConn.getPort(),
                targetConn.getDatabaseName(),
                targetConn.getUsername(),
                encryptionUtil.decrypt(targetConn.getEncryptedPassword())
        );

        SchemaDiffResponse diffResponse = comparisonService.compareSchemas(
                sourceSchema, targetSchema, sourceConn.getEnvironment(), targetConn.getEnvironment()
        );

        return ResponseEntity.ok(diffResponse);
    }
}
