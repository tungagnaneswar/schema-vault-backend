ALTER TABLE team_members
ADD COLUMN team_role VARCHAR(50) NOT NULL DEFAULT 'TEAM_MEMBER',
ADD COLUMN added_by BIGINT,
ADD CONSTRAINT fk_team_members_added_by FOREIGN KEY (added_by) REFERENCES users(id);
