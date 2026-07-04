CREATE TABLE member_project_assignments (
    id BIGSERIAL PRIMARY KEY,
    team_member_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    permission VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_member_project UNIQUE (team_member_id, project_id),
    CONSTRAINT fk_mpa_team_member FOREIGN KEY (team_member_id) REFERENCES team_members(id) ON DELETE CASCADE,
    CONSTRAINT fk_mpa_project FOREIGN KEY (project_id) REFERENCES db_connections(id) ON DELETE CASCADE
);

CREATE INDEX idx_mpa_team_member ON member_project_assignments(team_member_id);
