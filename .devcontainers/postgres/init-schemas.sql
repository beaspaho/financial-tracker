-- docker/init-schemas.sql
CREATE SCHEMA IF NOT EXISTS app;
GRANT ALL ON SCHEMA app TO app;

-- Ensure the app user has rights to create tables in this schema
ALTER DEFAULT PRIVILEGES IN SCHEMA app 
  GRANT ALL ON TABLES TO app;
ALTER DEFAULT PRIVILEGES IN SCHEMA app 
  GRANT ALL ON SEQUENCES TO app;