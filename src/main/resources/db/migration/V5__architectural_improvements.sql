ALTER TABLE db_connections
    ADD COLUMN engine VARCHAR(50) DEFAULT 'POSTGRES',
    ADD COLUMN included_schemas TEXT,
    ADD COLUMN excluded_tables TEXT;

CREATE TABLE schema_snapshots (
    id BIGSERIAL PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_snapshot_connection FOREIGN KEY (connection_id) REFERENCES db_connections (id) ON DELETE CASCADE
);

CREATE TABLE compare_jobs (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    source_snapshot_id BIGINT NOT NULL,
    target_snapshot_id BIGINT NOT NULL,
    result_data JSONB,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    CONSTRAINT fk_job_source_snapshot FOREIGN KEY (source_snapshot_id) REFERENCES schema_snapshots (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_target_snapshot FOREIGN KEY (target_snapshot_id) REFERENCES schema_snapshots (id) ON DELETE CASCADE
);
