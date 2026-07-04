ALTER TABLE compare_jobs
ADD COLUMN project_id BIGINT,
ADD COLUMN created_by_id BIGINT,
ADD COLUMN summary_statistics JSONB,
ADD COLUMN duration_ms BIGINT,
ADD COLUMN reason TEXT,
ADD COLUMN tags JSONB;

ALTER TABLE compare_jobs
ADD CONSTRAINT fk_compare_jobs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL,
ADD CONSTRAINT fk_compare_jobs_created_by FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE SET NULL;
