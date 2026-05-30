package com.gnanadhan.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnanadhan.app.dto.schema.SchemaModel;
import com.gnanadhan.app.entity.DbConnection;
import com.gnanadhan.app.entity.SchemaSnapshot;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.SchemaSnapshotRepository;
import com.gnanadhan.app.service.extractor.SchemaExtractor;
import com.gnanadhan.app.service.extractor.SchemaExtractorFactory;
import com.gnanadhan.app.service.security.SecretManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final SchemaSnapshotRepository snapshotRepository;
    private final DbConnectionRepository dbConnectionRepository;
    private final SchemaExtractorFactory extractorFactory;
    private final SecretManager secretManager;
    private final ObjectMapper objectMapper;

    public SchemaSnapshot createSnapshot(Long connectionId) {
        DbConnection connection = dbConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String decryptedPassword = secretManager.decrypt(connection.getEncryptedPassword());
        
        SchemaExtractor extractor = extractorFactory.getExtractor(connection.getEngine());
        SchemaModel schemaModel = extractor.extract(connection, decryptedPassword);

        try {
            String snapshotJson = objectMapper.writeValueAsString(schemaModel);
            SchemaSnapshot snapshot = SchemaSnapshot.builder()
                    .connection(connection)
                    .snapshotData(snapshotJson)
                    .build();
            return snapshotRepository.save(snapshot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize schema model", e);
        }
    }

    public Page<SchemaSnapshot> getSnapshotsByConnection(Long connectionId, int page, int size) {
        return snapshotRepository.findByConnectionId(connectionId, PageRequest.of(page, size));
    }
    
    public SchemaSnapshot getSnapshotById(Long snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found"));
    }
}
