CREATE TABLE lessons (
    id         UUID         PRIMARY KEY,
    course_id  UUID         NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    position   INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX lessons_course_id_idx ON lessons (course_id);
