# --- Add wpId to user table

# --- !Ups

ALTER TABLE user ADD COLUMN wpId INT NOT NULL DEFAULT 0 AFTER id;

# --- !Downs

ALTER TABLE user DROP COLUMN wpId;
