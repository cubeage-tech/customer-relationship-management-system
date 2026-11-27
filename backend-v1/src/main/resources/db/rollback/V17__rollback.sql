-- Manual rollback for V17__add_sales_teams.sql (Flyway Community has no undo).
-- Not on the Flyway path, never runs automatically.
--
-- Fast, no-schema-change rollback first: set app.features.sales-team-scope=false and
-- restart — sales managers go back to tenant-wide visibility; the tables stay unused.
--
-- Full rollback (drops team assignments; the app must be on a build without the
-- SalesTeam entity / User.team mapping, or Hibernate validation will fail at startup):

BEGIN;
ALTER TABLE users DROP COLUMN IF EXISTS team_id;
DROP TABLE IF EXISTS sales_teams;
DELETE FROM flyway_schema_history WHERE version = '17';
COMMIT;
