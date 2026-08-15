package com.schemavault.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemavault.app.dto.schema.SchemaModel;
import com.schemavault.app.entity.DatabaseEngine;
import com.schemavault.app.entity.DbConnection;
import com.schemavault.app.entity.SchemaSnapshot;
import com.schemavault.app.repository.DbConnectionRepository;
import com.schemavault.app.repository.SchemaSnapshotRepository;
import com.schemavault.app.service.SnapshotService;
import com.schemavault.app.service.extractor.SchemaExtractor;
import com.schemavault.app.service.extractor.SchemaExtractorFactory;
import com.schemavault.app.service.security.SecretManager;
import com.schemavault.app.service.security.CurrentUserService;
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

    @Mock
    private DbConnectionService dbConnectionService;
    @Mock
    private CurrentUserService currentUserService;

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
        connection.setEngine(DatabaseEngine.POSTGRES);
        connection.setEncryptedPassword("encrypted");

        when(dbConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        when(secretManager.decrypt("encrypted")).thenReturn("plain");
        when(extractorFactory.getExtractor(DatabaseEngine.POSTGRES)).thenReturn(schemaExtractor);

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
