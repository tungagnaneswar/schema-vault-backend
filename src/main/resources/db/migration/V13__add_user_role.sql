-- Add standard USER role for self-registered public accounts
INSERT INTO roles (name) VALUES ('USER') ON CONFLICT (name) DO NOTHING;
