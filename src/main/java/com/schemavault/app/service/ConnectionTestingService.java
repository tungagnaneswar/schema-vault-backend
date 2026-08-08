package com.schemavault.app.service;

import com.schemavault.app.entity.DatabaseEngine;
import com.schemavault.app.exception.DatabaseConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;

@Service
@Slf4j
public class ConnectionTestingService {

    public void testConnection(DatabaseEngine engine, String host, int port, String database, String username,
            String password) {
        String url = engine.buildJdbcUrl(host, port, database);
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            if (conn.isValid(5)) {
                log.info("Connection test successful for {}://{}:{}/{}", engine, host, port, database);
            } else {
                throw new DatabaseConnectionException("Connection is not valid");
            }
        } catch (Exception e) {
            log.error("Database connection failed", e);
            throw new DatabaseConnectionException("Database connection failed: " + e.getMessage(), e);
        }
    }
}
