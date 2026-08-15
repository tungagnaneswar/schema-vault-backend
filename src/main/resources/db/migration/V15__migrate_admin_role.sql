-- Migration: Migrate legacy ADMIN role users to USER role
UPDATE users 
SET role_id = (SELECT id FROM roles WHERE name = 'USER')
WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN');
