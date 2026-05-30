package com.gnanadhan.app.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DBHealthRepository {

    private final DataSource dataSource;

    public boolean checkDbConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            log.error("DB health check failed", e);
            return false;
        }
    }
}
