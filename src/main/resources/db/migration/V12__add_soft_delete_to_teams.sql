-- Add soft delete flag to teams
ALTER TABLE teams ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE NOT NULL;

-- Drop the old unique constraint on team name
ALTER TABLE teams DROP CONSTRAINT IF EXISTS teams_name_key;

-- Add a partial unique index so active teams must have unique names, but soft-deleted teams can share names
CREATE UNIQUE INDEX idx_teams_name_unique ON teams (name) WHERE is_deleted = false;
