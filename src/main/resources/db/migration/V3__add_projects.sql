CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE db_connections ADD COLUMN project_id BIGINT;

DO $$ 
DECLARE 
    admin_id BIGINT;
    default_project_id BIGINT;
BEGIN
    SELECT id INTO admin_id FROM users ORDER BY id ASC LIMIT 1;
    
    IF admin_id IS NOT NULL THEN
        INSERT INTO projects (name, description, created_by) 
        VALUES ('Legacy Connections', 'Default project for pre-existing connections', admin_id)
        ON CONFLICT (name) DO NOTHING
        RETURNING id INTO default_project_id;
        
        IF default_project_id IS NULL THEN
            SELECT id INTO default_project_id FROM projects WHERE name = 'Legacy Connections';
        END IF;
        
        UPDATE db_connections SET project_id = default_project_id WHERE project_id IS NULL;
    END IF;
END $$;

ALTER TABLE db_connections ADD CONSTRAINT fk_db_connections_project FOREIGN KEY (project_id) REFERENCES projects(id);
-- Making it NOT NULL might fail if there's no users and someone somehow added a db connection without a user? Impossible due to FK.
-- But just in case, we won't add NOT NULL at DB level to prevent migration errors, application will enforce it.
-- Or better, we can add it safely.
ALTER TABLE db_connections ALTER COLUMN project_id SET NOT NULL;
