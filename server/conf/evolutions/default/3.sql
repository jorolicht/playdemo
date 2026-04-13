# --- Add org to user table

# --- !Ups

ALTER TABLE user ADD COLUMN org VARCHAR(100) DEFAULT '' AFTER password;

# --- !Downs

ALTER TABLE user DROP COLUMN org;
