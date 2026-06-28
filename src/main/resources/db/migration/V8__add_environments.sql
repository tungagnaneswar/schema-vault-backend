CREATE TABLE environments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    sequence INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (name, project_id)
);

-- Add environment_id to db_connections
ALTER TABLE db_connections ADD COLUMN environment_id BIGINT;

-- Migrate data
DO $$ 
DECLARE
    r RECORD;
    new_env_id BIGINT;
BEGIN
    -- Create default environments for existing projects based on their current connections
    FOR r IN SELECT DISTINCT project_id, environment FROM db_connections WHERE project_id IS NOT NULL LOOP
        INSERT INTO environments (name, project_id) 
        VALUES (r.environment, r.project_id)
        ON CONFLICT (name, project_id) DO NOTHING
        RETURNING id INTO new_env_id;
        
        IF new_env_id IS NULL THEN
            SELECT id INTO new_env_id FROM environments WHERE name = r.environment AND project_id = r.project_id;
        END IF;

        UPDATE db_connections 
        SET environment_id = new_env_id 
        WHERE project_id = r.project_id AND environment = r.environment;
    END LOOP;
END $$;

-- Set constraints
ALTER TABLE db_connections ADD CONSTRAINT fk_db_connections_environment FOREIGN KEY (environment_id) REFERENCES environments(id);

-- Check if there are any orphaned connections, they shouldn't exist due to previous NOT NULL on project_id and environment
-- Drop old columns from db_connections
ALTER TABLE db_connections DROP CONSTRAINT fk_db_connections_project;
ALTER TABLE db_connections DROP COLUMN project_id;
ALTER TABLE db_connections DROP COLUMN environment;

-- Make environment_id NOT NULL
ALTER TABLE db_connections ALTER COLUMN environment_id SET NOT NULL;
