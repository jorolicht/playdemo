# --- Add description and roles to user table

# --- !Ups

ALTER TABLE user ADD COLUMN description TEXT AFTER picUrl;
ALTER TABLE user ADD COLUMN roles TEXT AFTER description;

# --- !Downs

ALTER TABLE user DROP COLUMN description;
ALTER TABLE user DROP COLUMN roles;
