-- Add soft delete flags
ALTER TABLE projects ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE db_connections ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE NOT NULL;

-- Drop the old unique constraint on project name (which blocks creating a project with a name that is currently soft-deleted)
ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_name_key;

-- Add a partial unique index so active projects must have unique names, but soft-deleted projects can share names
CREATE UNIQUE INDEX idx_projects_name_unique ON projects (name) WHERE is_deleted = false;
