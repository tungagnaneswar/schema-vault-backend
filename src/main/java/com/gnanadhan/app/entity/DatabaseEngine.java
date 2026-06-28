package com.gnanadhan.app.entity;

/**
 * Supported database engines.
 * <p>
 * Each constant carries its own JDBC URL template and default port,
 * making the enum self-describing. Adding a new database engine is a
 * single-line addition — no switch statements or if-else chains elsewhere.
 * </p>
 */
public enum DatabaseEngine {

    POSTGRES("jdbc:postgresql://%s:%d/%s", 5432),
    MYSQL("jdbc:mysql://%s:%d/%s", 3306);

    private final String jdbcUrlTemplate;
    private final int defaultPort;

    DatabaseEngine(String jdbcUrlTemplate, int defaultPort) {
        this.jdbcUrlTemplate = jdbcUrlTemplate;
        this.defaultPort = defaultPort;
    }

    /**
     * Builds a fully-qualified JDBC URL for this engine.
     */
    public String buildJdbcUrl(String host, int port, String database) {
        return String.format(jdbcUrlTemplate, host, port, database);
    }

    public int getDefaultPort() {
        return defaultPort;
    }
}
