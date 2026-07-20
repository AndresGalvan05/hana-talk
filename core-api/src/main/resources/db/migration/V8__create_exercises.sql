CREATE TABLE exercises (
    id             UUID         PRIMARY KEY,
    lesson_id      UUID         NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    type           VARCHAR(20)  NOT NULL,
    prompt         TEXT         NOT NULL,
    options_json   TEXT,
    correct_answer TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX exercises_lesson_id_idx ON exercises (lesson_id);

CREATE TABLE exercise_attempts (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id      UUID         NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    submitted_answer TEXT         NOT NULL,
    is_correct       BOOLEAN      NOT NULL,
    attempted_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX exercise_attempts_exercise_id_idx ON exercise_attempts (exercise_id);
CREATE INDEX exercise_attempts_user_id_idx ON exercise_attempts (user_id);
