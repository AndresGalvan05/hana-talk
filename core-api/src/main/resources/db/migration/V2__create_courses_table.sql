CREATE TABLE courses (
    id          UUID         PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    language    VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
