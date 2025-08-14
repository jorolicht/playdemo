# --- First database schema

# --- !Ups

CREATE TABLE user (
  id          int NOT NULL AUTO_INCREMENT,
  email       varchar(50) NOT NULL,
  firstname   varchar(50) NOT NULL,
  lastname    varchar(50) NOT NULL,
  picUrl      varchar(100) DEFAULT NULL,
  locale      varchar(8) DEFAULT NULL,
  verified    tinyint DEFAULT NULL,
  password    varchar(64) DEFAULT NULL,
  request     bigint DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY email (email)
);


# --- !Downs

drop table if exists user;
