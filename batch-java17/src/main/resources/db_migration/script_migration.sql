-- 1__create_person.sql

CREATE TABLE person (
    id BIGINT NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    salaire_encrypted TEXT NOT NULL,
    date_naissance DATE,
    ville VARCHAR(100),

    PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4;
