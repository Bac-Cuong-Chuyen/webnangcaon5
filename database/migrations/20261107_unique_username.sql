-- Run once on an existing PostgreSQL staging database before deploying this change.
-- Resolve any rows returned by this query before adding the constraint.
SELECT username, COUNT(*) FROM user_account GROUP BY username HAVING COUNT(*) > 1;

BEGIN;

ALTER TABLE user_account
    ADD CONSTRAINT uk_user_account_username UNIQUE (username);

ALTER TABLE user_account ADD COLUMN version bigint NOT NULL DEFAULT 0;

COMMIT;
