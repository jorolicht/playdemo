# --- Add username to user table

# --- !Ups

ALTER TABLE user ADD COLUMN username VARCHAR(50) DEFAULT '' AFTER id;

# --- !Downs

ALTER TABLE user DROP COLUMN username;
