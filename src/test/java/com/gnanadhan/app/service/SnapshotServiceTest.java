package com.gnanadhan.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnanadhan.app.dto.schema.SchemaModel;
import com.gnanadhan.app.entity.DbConnection;
import com.gnanadhan.app.entity.SchemaSnapshot;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.SchemaSnapshotRepository;
import com.gnanadhan.app.service.extractor.SchemaExtractor;
import com.gnanadhan.app.service.extractor.SchemaExtractorFactory;
import com.gnanadhan.app.service.security.SecretManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnapshotServiceTest {

    @Mock
    private SchemaSnapshotRepository snapshotRepository;
    @Mock
    private DbConnectionRepository dbConnectionRepository;
    @Mock
    private SchemaExtractorFactory extractorFactory;
    @Mock
    private SecretManager secretManager;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SchemaExtractor schemaExtractor;

    @InjectMocks
    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createSnapshot_Success() throws Exception {
        DbConnection connection = new DbConnection();
        connection.setId(1L);
        connection.setEngine("POSTGRES");
        connection.setEncryptedPassword("encrypted");

        when(dbConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        when(secretManager.decrypt("encrypted")).thenReturn("plain");
        when(extractorFactory.getExtractor("POSTGRES")).thenReturn(schemaExtractor);
        
        SchemaModel schemaModel = SchemaModel.builder().databaseName("testdb").build();
        when(schemaExtractor.extract(connection, "plain")).thenReturn(schemaModel);
        
        String json = "{}";
        when(objectMapper.writeValueAsString(schemaModel)).thenReturn(json);
        
        SchemaSnapshot savedSnapshot = new SchemaSnapshot();
        savedSnapshot.setId(10L);
        savedSnapshot.setSnapshotData(json);
        
        when(snapshotRepository.save(any(SchemaSnapshot.class))).thenReturn(savedSnapshot);

        SchemaSnapshot result = snapshotService.createSnapshot(1L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(snapshotRepository, times(1)).save(any(SchemaSnapshot.class));
    }
}
