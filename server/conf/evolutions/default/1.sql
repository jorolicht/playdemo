# --- First database schema

# CREATE DATABASE playdemo; should be run manually before applying evolutions

# --- !Ups

CREATE TABLE user (
  id          VARCHAR(36) NOT NULL,
  email       VARCHAR(50) NOT NULL,
  firstname   VARCHAR(50),
  lastname    VARCHAR(50),
  picUrl      VARCHAR(100) DEFAULT NULL,
  locale      VARCHAR(8) DEFAULT NULL,
  verified    TINYINT DEFAULT NULL,
  password    VARCHAR(64) DEFAULT NULL,
  entryTime   BIGINT DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY email (email)
);

# --- !Downs

drop table if exists user;



-- CREATE TABLE users (
--   id UUID PRIMARY KEY,
--   email VARCHAR(255) UNIQUE NOT NULL
-- );

-- CREATE TABLE passkeys (
--   user_id UUID,
--   credential_id BLOB,
--   public_key BLOB,
--   sign_count BIGINT,
--   PRIMARY KEY (credential_id)
-- );